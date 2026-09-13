# 导入分析链路的网络健壮性与失败可观测性

> 起因：一次真实的"识别失败"，Logcat 只留下 `worker exception: java.net.SocketException: Software caused connection abort`，
> UI 上只有两个字"失败"，没有原因、没有重试、没有通知。本文定义这条链路的错误契约与治理方案。

## 1. 事故复盘

### 1.1 现场日志的三条线索

| 日志 | 判定 | 说明 |
|---|---|---|
| `onNetworkChanged() not implemented in ...SystemJobService / JobInfoSchedulerService` | 噪音 | API 34 新增的 `JobService` 回调，androidx.work 与 GMS datatransport 都未实现，W 级警告 |
| `Failed to connect to firebaselogging.googleapis.com` | 噪音（但有信号） | GMS 遥测上报连不上 Google，自身 return 500 结束；侧面说明当时设备出网质量差 |
| `SocketException: Software caused connection abort` @ `Http2Reader.nextFrame` | **真错误** | 到 `api.deepseek.com` 的 HTTP/2 连接被中途重置，在读帧线程抛出，传导到所有在飞的 stream |

两个 worker 在 `17:06:06.439` / `.440` 同时失败。`AppContainer.aiClient` 是 lazy 单例，只有一个 `OkHttpClient`
和一个连接池，两个并发分析在**同一条 HTTP/2 连接上多路复用**，连接一断双双阵亡。这是共享连接的放大效应，不是巧合。

### 1.2 根因链

```
DeepSeekClient.post()            只把非 2xx 包成 AiException，IOException 原样抛出
        ↓
ImportInteractor.analyze()       只 catch AiException → SocketException 穿透
        ↓
AnalyzePostWorker catch(Throwable)  Log.e("worker exception") → markFailed → Result.success()
```

三个缺口叠加出来的后果：

1. **无重试** —— 瞬时抖动被判为终态失败，`BackoffPolicy.EXPONENTIAL` 形同摆设。
2. **无告知** —— `notifyDone("分析失败", reason)` 只写在 `ImportResult.Failed` 分支，catch 路径一条通知都不发。
3. **无线索** —— Room 只存 `status=FAILED`，无 reason 列；UI 映射成 `PostBadge.Label("失败")`，且没有重试入口。

## 2. 目标

1. 瞬时网络故障**自愈**，不消耗用户注意力。
2. 任何终态失败都**有原因、有通知、有重试入口**。
3. 每个阶段（resolve / fetch / OCR / extract / match / answer）**可观测**，日志能直接定位卡点。
4. 确定性失败（Key 错、模型输出不可解析）**不重试**，不烧 token。

## 3. 分层架构

```
┌─ UI 层 ──────────────────────────────────────────────────────────┐
│ HomeScreen  失败徽章 + 原因文案 + 「重试」按钮                     │
│ HomeViewModel.onRetry(post) → ImportScheduler.retryUrl(id, url)  │
└──────────────────────────────────────────────────────────────────┘
┌─ 任务层 ─────────────────────────────────────────────────────────┐
│ AnalyzePostWorker  可重试 → Result.retry()（上限 3，退避 30s 起） │
│                    不可重试 → markFailed(id, reason) + 通知       │
│                    CancellationException → rethrow，不写失败态    │
│ AnalyzeGate        Semaphore(1)，分析任务串行，保护连接与 token    │
└──────────────────────────────────────────────────────────────────┘
┌─ 领域/编排层 ────────────────────────────────────────────────────┐
│ ImportResult.Failed(reason, retryable)                           │
│ ImportInteractor  AiException → Failed，retryable 原样透传        │
└──────────────────────────────────────────────────────────────────┘
┌─ 错误语义层 ─────────────────────────────────────────────────────┐
│ AiException(message, retryable, cause)                           │
│ DeepSeekClient  IOException → retryable=true                      │
│                 401/403/404 → false；408/429/5xx → true           │
│                 JSON 解析失败 → false                             │
└──────────────────────────────────────────────────────────────────┘
┌─ 网络层 data/net ────────────────────────────────────────────────┐
│ HttpClients  单一 ConnectionPool、pingInterval 保活、按用途分档    │
│ RetryInterceptor  IOException/408/429/5xx 指数退避 + 抖动          │
│ NetEventListener  DNS/连接/TLS 阶段耗时与失败落日志                │
└──────────────────────────────────────────────────────────────────┘
```

## 4. 错误分类矩阵

这是整套方案的契约。新增 provider 或新增网络调用点时，按此表归类。

| 场景 | 异常/状态码 | retryable | 用户文案 |
|---|---|---|---|
| 连接被重置、读超时、DNS 失败 | `IOException` 家族 | ✅ | 网络中断，稍后自动重试 |
| 请求超时 | `408` | ✅ | 服务响应超时 |
| 触发限流 | `429` | ✅（读 `Retry-After`） | 请求过于频繁 |
| 服务端错误 | `5xx` | ✅ | 服务暂时不可用 |
| Key 未配置 / 无效 / 无权限 | 空 Key、`401`、`403` | ❌ | 请在设置中检查 API Key |
| 模型或路径不存在 | `404` | ❌ | 接口地址配置有误 |
| 余额不足 | `402` | ❌ | 账户余额不足 |
| 模型输出无法解析 | `AiException("未找到有效 JSON")` | ❌ | 模型返回格式异常，请重试一次 |
| 协程被取消 | `CancellationException` | — | 不算失败，不落库、不通知 |

**`runCatching` 禁用于协程边界。** `runCatching` 会吞掉 `CancellationException`，导致任务被取消时把行标成
`FAILED`，随后在已取消的 scope 上写库又会二次失败。需要吞异常时用显式
`catch (c: CancellationException) { throw c } catch (e: Exception) { ... }`。

## 5. 网络层设计要点

- **单一连接池**：四个 client（LLM / MCP / 页面抓取 / 图片下载）共享 `ConnectionPool`，避免连接数膨胀，
  也让降级策略有统一落点。
- **`pingInterval(20s)`**：HTTP/2 保活。死连接由 ping 及早发现，而不是卡到 120s 读超时才报错。
- **重试与 `callTimeout` 互斥**：`callTimeout` 统计的是整次 call 的墙钟时间，会把后续 attempt 砍掉。
  应用层重试打开后，LLM client 不再设 `callTimeout`，总时长由 `maxAttempts × (读超时 + 退避)` 界定。
- **流式请求不重试**：SSE 一旦吐出增量再重试就会产生重复文本。`postStream` 使用不带 `RetryInterceptor`
  的 client，其瞬时失败交给任务层整轮重试。
- **H2 → H1.1 降级**：`Software caused connection abort` 在国内网络 + 代理环境下对 H2 长连接高发。
  同一 host 连续失败达阈值后切到 `protocols(HTTP_1_1)` 的备用 client 再试一次，这是最有效的止血手段。
- **阻塞调用归位**：`HtmlFetcher.fetch` / `UrlResolver.resolve` 是同步网络调用，原先跑在
  `CoroutineWorker` 的 `Dispatchers.Default` 上，并发时会把 CPU 调度器线程占满。
  在 `ContentReader` 调用点用 `withContext(Dispatchers.IO)` 包住（保持接口同步，测试无需改造）。

## 6. 状态机（修订）

```
PENDING ──→ READING ──→ ANALYZING ──→ DONE
   ↑           │            │
   │           ↓            ↓
   │      NEEDS_MANUAL   FAILED(reason)
   │                        │
   └────────────────────────┘  可重试异常：回落 PENDING + Result.retry()
                                用户点「重试」：retryUrl() 重置为 PENDING
```

`READING` 此前从未被写入（worker 直接跳到 `ANALYZING`），本次补上，读取阶段卡住时用户能看出区别。

## 7. 可观测性

- **固定 TAG**：`OfferMate`（任务）/ `OfferMateAI`（模型）/ `OfferMateOCR`（识别）/ `OfferMateNet`（网络）。
- **失败原因落库**：`imported_post.failureReason`，随 `Post` 透到 UI，徽章显示 `失败 · <原因>`。
- **阶段日志**：网络层 `NetEventListener` 打 DNS/连接/TLS 阶段耗时；
  `ImageFetcher` 下载失败不再静默（原先 `runCatching{}.getOrNull()` 全吞，
  图片面经识别不出来时无法区分"下载失败"与"OCR 无字"）。
- **通知不可依赖**：API 33+ 无 `POST_NOTIFICATIONS` 时 `notifyDone` 静默 return，
  所以 UI 内的原因 + 重试是唯一保底通道，不是可选项。

## 8. 实施批次

| 批次 | 内容 | 文件 |
|---|---|---|
| P0-1 | 统一网络层 + 重试拦截器 + 保活 | `data/net/HttpClients.kt`、`RetryInterceptor.kt`、`NetEventListener.kt` |
| P0-2 | 异常分类 | `agent/AiClient.kt`、`agent/DeepSeekClient.kt` |
| P0-3 | 结果契约与透传 | `data/importer/ImportResult.kt`、`ImportInteractor.kt` |
| P0-4 | 任务层重试与串行化 | `work/AnalyzePostWorker.kt`、`work/AnalyzeGate.kt`、`work/ImportScheduler.kt` |
| P1-1 | 失败原因落库 + UI 反馈 | entity / DAO / DB version、`PostStore`、`PostMappers`、`Post`、Home 层 |
| P1-2 | 阻塞调用归位 + 阶段日志 | `data/reader/*` |
| P2 | 日志噪音清理（升级 androidx.work、Firebase 采集开关） | `gradle/libs.versions.toml`、Manifest |

## 9. 验证矩阵

| 用例 | 手法 | 期望 |
|---|---|---|
| 瞬时连接重置 | MockWebServer `SocketPolicy.DISCONNECT_AT_START` | `RetryInterceptor` 重试后成功 |
| 持续网络故障 | 全程断网 | 重试耗尽 → `FAILED` + 原因"网络中断" + 通知 + 重试按钮 |
| Key 无效 | 填错 Key | 一次即终止，不重试，原因指向设置 |
| 生成中途断网 | `adb shell svc data disable` | worker `Result.retry()`，恢复网络后自动完成 |
| 任务被取消 | 导入中划掉进程 / cancel work | 不落 `FAILED`，恢复后 WorkManager 续跑 |
| 并发导入 | 连续提交两条链接 | 串行执行，不共享同一在飞连接 |
