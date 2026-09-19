---
trust: L1
anchors:
  - app/src/main/java/com/jk/offermate/work/AnalyzeGate.kt#withSlot
  - app/src/main/java/com/jk/offermate/data/net/HttpClients.kt
  - app/src/main/java/com/jk/offermate/data/net/Http2Health.kt
verified_commit: 63c65fb
verified_at: 2026-09-19
supersedes: null
---

# 并发任务共享 HTTP/2 连接，一条断全部死

## 现象

两个导入任务同时跑时，**在同一毫秒双双失败**，报错都是连接被重置一类的 `IOException`。单独跑任何一条都正常。

## 根因

两个 `AnalyzePostWorker` 共享同一个 `OkHttpClient` 的连接池，请求在**同一条 HTTP/2 连接上多路复用**。HTTP/2 的多路复用意味着连接是共享故障域：连接一断，跑在它上面的所有请求一起阵亡。表面上像"AI 服务挂了"，实际是一条 TCP 连接的问题被放大到全部并发任务。

WorkManager 默认并发度是 4，所以批量分享时很容易触发。

## 规避规则

1. **AI 分析任务必须串行**：走 `AnalyzeGate.withSlot { … }`（`Semaphore(permits = 1)`）。新增任何"会调 LLM 的后台 worker"都要进这个闸门，不要另起并发。
2. 连接故障要**记账并降级**，不要只重试：`Http2Health.recordAbort(host)` 标记该 host 后续走 HTTP/1.1（每请求独占连接，把故障隔离在单个请求内）。
3. 判断是否"连接被掀掉"用 `NetErrors.looksLikeConnectionAbort(e)`，不要自己 match 异常类型。
4. 串行化的附带收益：避免并发烧 token、避免触发服务端限流。**不要为了"快一点"把它改成并发。**

## 证据

- `work/AnalyzeGate.kt` 的类注释直接记录了这次事故的归因，并指向 `docs/plan/network-resilience.md` 第 1.1 节。
- commit `641c650`「通过完善网络请求韧性与重试机制实现导入和AI分析稳定性提升」。
- `DeepSeekClient.executeWithHttp1Fallback` 实现了 HTTP/1.1 降级重试；`Http2HealthTest` 覆盖降级状态机。

## 触发条件

任何"多个后台任务并发调同一个 LLM 端点"的场景。新增批量处理、预加载、定时刷新一类功能时会再次遇到。
