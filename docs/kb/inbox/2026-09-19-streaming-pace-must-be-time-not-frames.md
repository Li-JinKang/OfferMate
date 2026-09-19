---
trust: L2
anchors:
  - app/src/main/java/com/jk/offermate/ui/components/Typewriter.kt
verified_commit: 403709a
verified_at: 2026-09-19
supersedes: null
---

# 流式出字节拍必须按时间定，按固定帧数会在高刷屏上静默翻倍负载

回归由 `0e7f3fb` 引入，修复在 `403709a`（`fix(chat): 出字节拍改按时间定…`）。

## 现象

Honor 90 Pro（**120Hz**）上抓 System Trace，`om.jk.offermate` 主线程：

```
Choreographer#doFrame                     72.43%
└ traversal
  └ draw-VRI[MainActivity]
    └ HardwareRenderer#syncAndDrawFrame   56.84%
      └ postAndWait      self 23,379µs / 6 帧 ≈ 3.9ms/帧   ← 占满近一半帧预算
```

## 根因

`Typewriter` 的出字节拍曾写成**固定每 2 帧一次**（`EMIT_EVERY_N_FRAMES = 2`）。
在 60Hz 上是 33ms、约 30 次/秒（符合设计意图）；到 120Hz 上就是 17ms、**约 60 次/秒**。

每次出字都引发一整轮 重组 → 布局 → 重新录制绘制指令 → RenderThread 绘制，
而这串成本是按**每秒多少次**计的，跟刷新率无关。所以「每 N 帧」这种写法等于让负载随刷新率线性放大，
高刷屏反而被惩罚。

最坑的地方：当时的注释把它写成了优点——「把下游重解析/重排的次数压到**刷新率的一半**」。
在 60Hz 上这句话没错，在 120Hz 上它描述的正是 bug。**看到「刷新率的一半」这类表述要警觉，
那说明频率被绑死在刷新率上了。**

## 规避规则

1. **节流目标写成时间，再按实测帧间隔换算成整数帧数去对齐。** 两头的好处都要：
   节拍均匀（整数帧，不会在 3/4 帧之间来回跳），频率不随刷新率漂移。

   ```kotlin
   private const val TARGET_EMIT_INTERVAL_MS = 32f
   // 循环里：
   val framesPerEmit = (TARGET_EMIT_INTERVAL_MS / (frameIntervalNanos / 1_000_000f))
       .roundToInt().coerceAtLeast(1)
   if (framesSinceEmit < framesPerEmit) continue
   ```

   换算结果：60Hz→2 帧、90Hz→3 帧、120Hz→4 帧、144Hz→5 帧，都落在 33~35ms。

2. **攒帧节流不能吃掉「恢复后的首次响应」。** 改成帧计数后有个副作用：每次 token 间隙恢复时
   要先攒满 `framesPerEmit` 帧才出字，等于每次停顿多压一个间隔的延迟（120Hz 上 33ms），
   观感是一顿一顿。要单独留一个「恢复后第一帧立刻出字」的旗标（`emitOnNextFrame`），
   攒帧只用于限制**持续输出**时的频率。

3. 帧间隔要实测并**丢弃异常值**（掉帧时的大间隔不能算进"这段时间该吐多少字"，否则卡一下就蹦一大段）。
   现有实现取 `MIN_FRAME_NANOS..MAX_FRAME_NANOS`（约 250Hz~25Hz）之外的一律忽略。

## 怎么读这张 trace（避免重复误判）

**`postAndWait` 不是应用代码，也不代表"耗时"。** 它在 `HardwareRenderer#syncAndDrawFrame` 之下，
是 UI 线程把绘制任务交给 RenderThread 后**阻塞等待**的时间——`children = 0`、self 就是全部，
说明这段时间主线程什么都没干。它高只说明瓶颈在绘制/GPU 侧，**往主线程里找热点是找不到的**。

它高了之后的分叉判断（展开同几帧的 RenderThread 行看谁占大头）：

| RenderThread 里占大头 | 含义 | 方向 |
|---|---|---|
| `DrawFrame` / `flush commands` | 绘制指令太多 | 减少同时绘制的内容量、减少裁剪层 |
| `dequeueBuffer` / `eglSwapBuffers` | GPU / 显示管线吃不下 | 减少 overdraw 与半透明合成 |

两者解法相反，所以**先量再改**。`Debug GPU Overdraw` 比 trace 更快能判断是不是 overdraw。

## 附带结论：主线程重组已经不是瓶颈，别再往那儿优化

同一份 trace 折算到每帧：

| 节点 | 每帧 |
|---|---|
| `Recomposer:recompose` | **0.08ms** |
| `AndroidOwner:measureAndLayout` | 0.68ms |
| `Record View#draw()` | 0.93ms |
| `postAndWait`（等待） | 3.9ms |

`0e7f3fb` 那轮压重组范围的改造已经把重组打到 0.08ms/帧。**继续优化重组/解析拿不到收益**，
瓶颈已整体转移到「每秒画多少次 × 每次画多少」。

同理已排除的还有：`StreamingMarkdown.blocks()` / `PartialMarkdown.sanitize()`
（见 `inbox/2026-09-19-markdown-renderer-streaming-perf.md` 第二节，实测 3000 字 61µs/帧），
以及底部 dock 的离屏合成（`BottomTabs.kt` 已把 `CompositingStrategy.Offscreen` 限制在
`alpha < 1` 的淡出过程，流式期间 alpha 恒为 1，走默认策略）。

## 证据

- 用户在 Honor 90 Pro 上抓的 System Trace（Top Down / Wall Clock Time，选中 6 帧），
  数值即上面两张表。**该 trace 的构建变体未确认**（release 或 profileable）。
- 回归引入：`0e7f3fb` `perf(chat): 压缩流式输出的重组范围…` 中的 `EMIT_EVERY_N_FRAMES = 2`。
- 修复后的换算表是按帧间隔算术推出的，**未在真机复测**——预期 120Hz 上绘制帧数减半，
  需要重抓一次 trace 确认 `postAndWait` 是否随之下降。

## 触发条件

任何"每 N 帧做一次"的节流：打字机出字、流式滚动贴底、自绘动画的降频。
本项目还有一处相关但**不受影响**的节流：`ChatViewModel.PUBLISH_INTERVAL_NANOS`（16ms 墙钟）。
它只推进"目标长度"，可视频率由打字机控制；且 `revealed` 那层 `derivedStateOf` 有结果相等性检查，
`displayLen` 没前进时截断结果不变、不会通知下游，所以高频发布不会穿透成重组。
