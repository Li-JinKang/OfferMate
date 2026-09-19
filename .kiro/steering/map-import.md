---
inclusion: auto
name: 导入与内容读取链路
description: 当需求涉及分享链接导入、手动粘贴正文、链接读取与短链展开、WebView 离屏渲染、小红书/牛客正文提取、图片面经 OCR、导入状态机与失败重试、WorkManager 后台分析任务、题目去重与落库时激活。
---

# 代码地图 · 导入与内容读取链路

完整地图见 `docs/kb/map/import-pipeline.md`，**动手前请先读它**（含完整调用链、降级链与状态机）。

#[[file:docs/kb/map/import-pipeline.md]]

## 三条最容易踩的

1. **终态失败也返回 `Result.success()`**（刻意如此，避免 WorkManager 重复重试）。排障看 `imported_post.failureReason` 与 Logcat TAG `OfferMate` / `OfferMateOCR`，不要找 WorkManager 的 FAILED 状态。
2. **重试复用同 id 会覆盖题目**（`id = "${postId}_$index"` + 先 `deleteByPost`），用户的刷题进度、编辑过的答案、追问会话关联会丢。
3. 兜底捕获**不能用 `runCatching`**，必须重抛 `CancellationException`（见 `docs/kb/pitfalls/coroutine-runcatching-cancellation.md`）。
