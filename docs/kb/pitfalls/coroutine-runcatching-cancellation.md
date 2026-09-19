---
trust: L1
anchors:
  - app/src/main/java/com/jk/offermate/data/importer/ImportInteractor.kt#tolerate
  - app/src/main/java/com/jk/offermate/work/AnalyzePostWorker.kt#safely
  - app/src/main/java/com/jk/offermate/data/reader/ContentReader.kt#read
verified_commit: 63c65fb
verified_at: 2026-09-19
supersedes: null
---

# `runCatching` 吞掉协程取消，任务被取消后继续跑

## 现象

任务已经被取消（用户退出、WorkManager 重新调度、超时），流程却**继续往下执行**，最后在已取消的 scope 上写库并抛出二次异常；日志里表现为莫名的"兜底失败"。

## 根因

`runCatching` 捕获 `Throwable`，把 `CancellationException` 也当成普通失败吞掉。而协程的取消**就是靠抛 `CancellationException` 传播**的——吞掉它等于把取消信号丢了：后续 `suspend` 调用继续执行，直到某个依赖已取消 scope 的操作再次炸掉。

同理，`catch (e: Exception)` 也会吞（`CancellationException` 是 `IllegalStateException` 的子类链上的 `Exception`）。

## 规避规则

在协程里兜底 IO / AI / DB 调用时，**不要用 `runCatching`**。用项目既有的两个辅助方法，或照它们的写法手写：

```kotlin
private suspend fun <T> tolerate(block: suspend () -> T): T? =
    try {
        block()
    } catch (c: CancellationException) {
        throw c          // 必须重抛
    } catch (e: Exception) {
        null
    }
```

- 编排层可失败步骤 → `ImportInteractor.tolerate`
- Worker 的兜底写库/通知 → `AnalyzePostWorker.safely`
- 只在**纯同步、无协程语义**的地方（如解析字符串、读 SharedPreferences）才可以用 `runCatching`

额外一条：**兜底动作自身也要包 try/catch**。兜底里再抛异常会让 worker 静默变成 `Result.failure()`，用户连失败态都看不到。

## 证据

- `ImportInteractor.tolerate` 与 `AnalyzePostWorker.safely` 的方法注释都写明了"不用 `runCatching`"的原因，并指向 `docs/plan/network-resilience.md` 第 4 节。
- `ContentReader.read` 里 WebView 兜底的 `catch (c: CancellationException) { throw c }` 是同一模式。
- `AnalyzePostWorker.doWork` 对 `CancellationException` 单独 `throw c` 并注释"取消不是失败，不能落 FAILED"。

## 触发条件

新增任何"可失败但要降级继续"的 suspend 步骤时。尤其注意 IDE 和习惯都会诱导你写 `runCatching`——它更短。
