---
trust: L2
anchors:
  - app/src/main/java/com/jk/offermate/ui/aichat/ChatViewModel.kt
  - app/src/main/java/com/jk/offermate/ui/aichat/AiChatHubScreen.kt
  - app/src/main/java/com/jk/offermate/ui/followup/FollowUpScreen.kt
  - app/src/main/java/com/jk/offermate/ui/components/Typewriter.kt
verified_commit: 403709a
verified_at: 2026-09-19
supersedes: null
---

# 约束：流式文本不得进入会话列表的数据流

本卡描述的结构由 `0e7f3fb`（`perf(chat): 压缩流式输出的重组范围…`）引入。
锚点 `Typewriter.kt` 在 `403709a` 中改过出字节拍（见
`streaming-pace-must-be-time-not-frames.md`），已核对本卡的三条规则均未受影响——
`rememberTypewriterText` 仍返回 `State<String>`、`fullText` 仍收 lambda——故 `verified_commit` 前移至该提交。

## 规则

AI 对话页的流式文本（`ChatViewModel.streamingText`）**只能被真正显示文字的那个叶子 composable 读取**。
它不得：

1. 被 combine 进 `ChatContent.messages`；
2. 以**值**的形式作为参数传给 `FollowUpScreen` 这类页面级 composable；
3. 在页面级作用域被读取（包括用 `by` 解构 `collectAsStateWithLifecycle()`）。

正在生成的那一块由 `FollowUpScreen` 里的 `StreamingTailRow` 单独渲染，
**整个流式期间只有它一个 composable 在高频重组**。

## 为什么

流式文本的更新频率是 SSE chunk 的到达频率（provider 侧约 20~50 次/秒），
叠加打字机的出字节拍后更高。而下游真正需要这个高频数据的，只有打字机的「目标长度」和尾块的文本。

一旦它进了列表数据流，每个 token 都会：重建整个消息列表 → 页面级 composable 重组 →
重建上百个行对象 → 重跑 LazyColumn 的 content lambda。也就是**为了让打字机知道"又多了三个字"，
把整页重算一遍**。

## 三个会静默撤销这个优化的写法

这几处都**不会**编译报错、不会有 lint 警告、code review 也极容易放过。

### 1）`by` 解构（一个字符的差别）

```kotlin
// 对：读取推迟到叶子，本 composable 不订阅
val streamingTextState = viewModel.streamingText.collectAsStateWithLifecycle()
FollowUpScreen(streamingText = { streamingTextState.value }, …)

// 错：本 composable 立刻订阅，每个 token 重组整页
val streamingText by viewModel.streamingText.collectAsStateWithLifecycle()
FollowUpScreen(streamingText = { streamingText }, …)
```

后者**看起来也传了 lambda**，但 `by` 已经让读取发生在页面作用域，lambda 只是个空壳。

### 2）把高频 state 无条件读在页面级

`FollowUpScreen` 里的 `settledBlocks` 必须写成
`if (showStreamingTail) settledBlocksState.value else emptyList()`。
去掉这个条件，页面在**空闲态**也会订阅流式 state。

### 3）打字机的返回值改回 `String`

`rememberTypewriterText` 刻意返回 `State<String>` 而不是 `String`：
状态要留在页面作用域（放进 LazyColumn 的 item 会随滚动被回收，状态一丢就从头重打一遍），
但读取点要在叶子。**返回 String 就等于把这两件事又绑回一起了。**

## 配套的两个机制（改动时别拆散）

- **`ChatContent` 必须是 data class，且 `messages` 与 `showStreamingTail` 原子发布。**
  拆成两个 StateFlow 的话，流式结束那一瞬间 Compose 可能只看到其中一个：
  先看到"不显示流式气泡"会让回答闪一下消失，先看到"消息已落库"会短暂出现两份。
  data class 的 equals 同时负责去重——`_streaming` 每来一个 chunk 都会让 combine 重算，
  但只要这两个字段没变，StateFlow 基于相等性的 conflation 就会把它丢掉，不产生发射。

- **已固化块与尾块用两层 `derivedStateOf` 分离。**
  `settledBlocks = streamBlocks.dropLast(1)` 的结果在块与块之间是不变的，
  `derivedStateOf` 自带结果相等性检查，于是页面只在"又写完一块"时重组（约 1~2 次/秒）。
  直接在页面里切块就没有这层过滤了。

## 证据

- `ChatViewModel`：`ChatContent` / `streamingText` / `streamBatched` 的注释记录了各自的意图。
- `AiChatHubScreen`：`streamingTextState` 处有「刻意不用 `by` 解构」的注释。
- `FollowUpScreen`：`streamBlocks` / `settledBlocksState` / `tailBlockState` 三层派生 +
  `StreamingTailRow` 的 KDoc。
- 触发本轮改造的实测：release 包 + Honor 90 Pro 上的 system trace，个别帧最长 20ms，
  但存在大量黄色帧、部分达 40ms（用户提供，非本人复现）。
- **未做真机验证**：改造后的帧数据尚未测量，"收益"只是机制推断。

## 顺带：本项目的帧预算是 8.33ms

主力验证机型 Honor 90 Pro 是 **120Hz** 屏。做流式/滚动这类性能判断时，
帧预算是 **8.33ms 而不是 16.67ms**，40ms 相当于**5 个**帧预算。
按 60Hz 估算会系统性低估问题严重程度（本轮一开始就按 16ms 算过，低估了一半）。

## 待补

`docs/kb/map/` 目前没有 AI 对话流式渲染链路的代码地图卡。这条链路跨 8 个文件、
已有 5 轮优化史（`887d571` / `f77f1fe` / `dc7112a` / `38a2c26` + 本轮），
每次改动都得把链路重读一遍。建议审阅时连同
`inbox/2026-09-19-markdown-renderer-streaming-perf.md` 一起，合并成一张 `map/chat-streaming.md`。
