---
trust: L1
anchors:
  - app/src/main/java/com/jk/offermate/share/ShareIntentParser.kt#extractLink
  - app/src/main/java/com/jk/offermate/ui/home/HomeViewModel.kt#canAnalyze
  - app/src/main/java/com/jk/offermate/work/ImportScheduler.kt#retryUrl
  - app/src/main/java/com/jk/offermate/work/AnalyzePostWorker.kt#doWork
  - app/src/main/java/com/jk/offermate/work/AnalyzeGate.kt#AnalyzeGate
  - app/src/main/java/com/jk/offermate/data/importer/ImportInteractor.kt#enrichWithImageOcr
  - app/src/main/java/com/jk/offermate/data/reader/ContentReader.kt#read
  - app/src/main/java/com/jk/offermate/data/reader/PostImageExtractor.kt
  - app/src/main/java/com/jk/offermate/data/local/PostStore.kt#saveSuccess
  - app/src/main/java/com/jk/offermate/data/dedup/QuestionDeduplicator.kt#isDuplicate
  - app/src/main/java/com/jk/offermate/domain/model/ImportStatus.kt
  - app/src/main/java/com/jk/offermate/data/local/entity/ImportedPostEntity.kt
verified_commit: 63c65fb
verified_at: 2026-09-19
supersedes: null
---

# 链接导入 → 读取 → OCR → 分析 → 落库

## 一句话

分享即入队、立刻可退出：先落 `PENDING` 记录再交 WorkManager。全链路**串行**（`AnalyzeGate` 只有 1 个许可），所有失败都要有可读原因落库，而且**终态失败也返回 `Result.success()`**。

## 调用链

```
① 入口
   分享     MainActivity.extractSharedText(ACTION_SEND + text/plain)
            → HomeViewModel.onSharedTextReceived → ShareIntentParser.extractLink
   手输     HomeViewModel.onExtract / onPasteAnalyze
   重试     HomeViewModel.onRetry（先看 Post.canRetry）
② 前置校验  HomeViewModel.canAnalyze()
              Key 未配置 → 硬拦截，不入队
              简历为空   → 仅 toast，继续入队
③ 入队      WorkManagerImportScheduler.enqueueUrl / enqueueText / retryUrl
              → PostStore.createPending(id, url)  【落 PENDING，首页立即可见】
              → enqueueUniqueWork("analyze_$id")，NetworkType.CONNECTED
                 BackoffPolicy.EXPONENTIAL 起点 30s
④ 执行      AnalyzePostWorker.doWork
              setForeground（runCatching 包住，失败只 warn）
              AnalyzeGate.withSlot { … }          【全局串行】
              markStatus(READING 或 ANALYZING)
⑤ 读取      ImportInteractor.importFromUrl → ContentReader.read（降级链见下）
⑥ OCR       ImportInteractor.enrichWithImageOcr
              PostImageExtractor 提图 → OkHttpImageFetcher 下载 → MlKitTextRecognizer
              → 拼成 "\n\n【图片识别内容】\n图1：\n…"
⑦ 分析      AnalysisPipeline.analyze：抽题 → 相关性(阈值 60) → 作答（任一步空则短路）
⑧ 分类      ImportInteractor.categorize → CategoryClassifier，新分类写回 CategoryRepository
              整段包在 tolerate{} 里：分类失败不影响导入
⑨ 落库      PostStore.saveSuccess → deleteByPost → dedupForInsert → insertAll → status=DONE
```

## ContentReader 降级链

| 顺序 | 动作 | 失败判定 |
|---|---|---|
| 1 | `urlResolver.resolve` 展开短链 | **事后**判定：展开后仍含 `xhslink`/`/share/jump`/`b23.tv`/`t.cn` → `notExpanded = true`（不 return，继续往下试） |
| 2 | `htmlFetcher.fetch` + `extractor.extract` | 非 2xx/异常 → html 为 null；提取出的 `PostContent.isUsable` 要求正文 ≥ `MIN_USABLE_LENGTH`(40) |
| 3 | `dynamicReader.read`（`WebViewContentReader`，离屏 WebView，20s 超时 + 轮询取 DOM） | 同样要求 `isUsable`；异常吞掉但**重抛 `CancellationException`** |
| 4 | `ReadResult.NeedsManualInput(resolvedUrl, reason)` | — |

**三档诊断原因**只在 `ContentReader.read` 末尾的 `when` 里生成（短链未展开 / 抓取失败 / 抓到但无正文），随后经 `PostStore.markNeedsManual` 落到 `imported_post.failureReason`，由首页卡片展示。改文案只改这一处。

正文提取内部顺序：小红书域名 → `XhsNoteExtractor`（解析 `__INITIAL_STATE__`）→ 否则 `Readability4J` → 再退 `Jsoup` body text。

## ImportStatus 状态机

6 个值：`PENDING` `READING` `ANALYZING` `DONE` `NEEDS_MANUAL_INPUT` `FAILED`（`isTerminal` = 后三者；`from(name)` 解析不到回落 `PENDING`）。

```
创建/重试 → PENDING
PENDING → READING（URL 模式）/ ANALYZING（MODE_TEXT 直接进）
成功 → DONE（清 failureReason，写 questionCount）
读取失败 → NEEDS_MANUAL_INPUT（带 reason）
可重试失败 或 瞬时异常，且 runAttemptCount < MAX_ATTEMPTS-1 → 回落 PENDING + Result.retry()
否则 → FAILED（带 reason）+ 通知 + Result.success()
CancellationException → 直接上抛，保持当前状态
```

## 改动清单

### 给 `ImportedPostEntity` / `QuestionEntity` 加列

1. 实体加字段（给默认值，需要查询就加 `@Index`）。
2. **升 `OfferMateDatabase.version` + 更新其上方版本注释**（红线，当前升版本清库）。
3. `PostStore` 的写入方法 + `ImportedPostDao` 的定向 UPDATE（`updateStatus`/`updateFailure`）要不要带上新列。
4. `PostMappers`（`toDomainPost`/`toQuestionEntities`/`toAnswered`）+ 领域模型 `Post`/`AnsweredQuestion`。
5. UI：`HomeUiState`/`HomeViewModel`/`HomeScreen`，题目相关还有 `ui/questions`、`ui/quiz`。
6. 单测：`PostMappersTest`、`PostStoreTest`（**内含手写 fake DAO，加 DAO 方法必须补**）、`HomeViewModelTest`。

### 新增一个链路环节 / 新的降级级别

1. 定义接口 + 实现放 `data/reader` 或 `data/ocr`，沿用"接口同步、实现注入"以便 JVM fake。
2. 编排进 `ImportInteractor`（构造参数设为**可空**以便单测跳过；异常用 `tolerate{}` 且放行 `CancellationException`）。
3. 装配在 `AppContainer` 的 `contentReader` / `importInteractor`。
4. 新结果态要同步 `ReadResult` / `ImportResult` 与 `ContentReader` 的 reason 分支。
5. 新可见阶段要加 `ImportStatus` 枚举值 + `AnalyzePostWorker` 里 `markStatus`，并检查 `isTerminal` 与 `Post.canRetry`。
6. `AnalyzePostWorker.handleResult` 的 `when` 是 exhaustive 的，编译会提醒补分支，**但重试语义要人工判断**。

## 约束与易错点

1. **终态失败返回 `Result.success()`**（刻意如此，避免 WorkManager 再重试）。所以 WorkManager 里几乎不会出现 FAILED 状态，排障只能看 `imported_post.failureReason` 与 Logcat（TAG `OfferMate` / `OfferMateOCR`）。
2. **`MAX_ATTEMPTS = 3`，判据是 `runAttemptCount < MAX_ATTEMPTS - 1`**（`runAttemptCount` 从 0 起）。退避起点 30s，因为网络抖动常在几十秒内自愈，10s 起会在同一故障窗口里连试三次白费。
3. **Key 校验只在 ViewModel 层**：分享入口、进程重启恢复的任务、排队期间 Key 被清掉，都会一路跑到 `AiException` 才失败。Worker 侧无二次校验。
4. **重试复用同 id，会覆盖题目**：`toQuestionEntities` 用 `id = "${postId}_$index"`，`saveSuccess` 先 `deleteByPost`。重试成功后用户在旧题上的 `practiced`、编辑过的答案、追问会话关联**全部丢失**。
5. **`resolvedUrl` 实际永远是 null**（没有任何写入路径），所以 `Post.sourceUrl` 总是取原始 `url`；重试重试的是原始短链。`enqueueText` 写的 `"手动粘贴"` 不以 http 开头，因此手动粘贴失败后没有重试入口。
6. **`NEEDS_MANUAL_INPUT` 转手动粘贴会新建记录**（新 UUID），不会把原记录改成 DONE。目前没有"把粘贴正文补到既有 id 上"的路径。
7. **全局串行**：一条读取卡住（WebView 最长 20s + 轮询）会阻塞后续所有导入，批量分享时表现为逐条完成。
8. **OCR 只在 URL 路径**：`importFromText` 不做 OCR，图片面经必须走链接导入。图片下载依赖 `Referer: xiaohongshu.com`，换图源可能 403。
9. **去重在落库瞬间**（`dedupForInsert`）：精确 `exactHash` 或"同 `bucketKey` 且 simhash 汉明距离 ≤ 3"即判重，**命中就跳过入库**（不合并、不计来源数）。只查同分桶候选 + 精确兜底，不扫全表。
10. `READING` 状态窗口极短：URL 模式先 `markStatus(READING)` 紧接着又 `markStatus(ANALYZING)` 才去读取，所以真正读取期间 UI 显示"分析中"——与注释意图不符，属已知瑕疵。

## 相关单测

`ContentReaderTest`（降级顺序 + reason）、`HtmlContentExtractorTest`、`XhsNoteExtractorTest`、`PostImageExtractorTest`、`UrlResolverTest`、`ImportInteractorTest`（编排）、`QuestionDeduplicatorTest`、`PostStoreTest`（状态流转 + 增量去重）、`PostMappersTest`、`ShareIntentParserTest`、`HomeViewModelTest`、`NetErrorsTest`/`RetryInterceptorTest`。

联网探针（默认跳过）：`LiveLinkReadingTest`、`ImageOcrProbeTest`。真机：`ImageOcrInstrumentedTest`。

⚠️ **`AnalyzePostWorker` 没有 WorkManager 测试**，状态机与重试语义改动无自动化兜底。
