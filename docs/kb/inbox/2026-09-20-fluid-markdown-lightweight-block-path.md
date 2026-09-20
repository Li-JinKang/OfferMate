---
trust: L2
anchors:
  - app/src/main/java/com/jk/offermate/ui/components/InlineMarkdown.kt
  - app/src/main/java/com/jk/offermate/ui/components/MarkdownText.kt#InlineBlockText
  - app/src/main/java/com/jk/offermate/ui/components/MarkdownText.kt#StreamingMarkdownTail
  - app/src/main/java/com/jk/offermate/ui/components/Typewriter.kt#adaptiveEmitInterval
  - app/src/main/java/com/jk/offermate/agent/StreamingTextBuffer.kt
verified_commit: 07f92fa
verified_at: 2026-09-20
supersedes: null
---

# 借 FluidMarkdown 的思路给流式尾块另开一条单节点渲染路径

本轮针对「低端机上流式输出帧超时」的改造记录。参考实现是蚂蚁开源的
[FluidMarkdown](https://github.com/antgroup/FluidMarkdown)（Apache-2.0），本地副本
`/Users/lijinkang/code/android/github/FluidMarkdown`，Android 侧源码在
`Android/AntFluid/fluid-markdown`。

**本卡只有源码核对与 JVM 度量，没有真机帧率数据。** 收益量级是推断值。

## 一、FluidMarkdown 哪些能借、哪些不能

它的 Android 实现是 Markwon + 一个 `TextView`（`PrinterMarkDownTextView`），与 Compose 架构差得远，
但有三条设计是跨框架成立的：

| FluidMarkdown 的做法 | 能否照搬 | 本项目对应动作 |
|---|---|---|
| 渲染单元始终是**一个 TextView + 一个 Spannable**，出字只做 `subSequence` + 搬 span + `setText` | 思路可借，实现不可搬 | 段落/标题/单列表项改成 **一两个 `BasicText` + 一个 `AnnotatedString`** |
| 解析频率 = 增量到达频率，出字频率 = 定时器（默认 25ms / 1 字符），两者解耦 | 已有等价物 | `Typewriter` 的双指针追赶 + `ChatViewModel.streamBatched` 的发布节流 |
| `OpacitySpan` 给末尾 10 个字符做 alpha 渐变，跟着出字推进（**不是每帧动画**） | 可搬 | `InlineMarkdown.fadeTail` |
| `setPrintParams(interval, chunkSize)` 静态配速 | 不够用 | 改成按实测帧间隔自适应（`adaptiveEmitInterval`） |
| `onMeasure` 里 `setMinHeight(已达到高度)` 防高度回缩 | 本项目没这个问题 | 未做 |
| `setFallbackLineSpacing(false)` 防行高跳变 | 已等价满足 | Compose 的 `TextStyle.lineHeight` 是固定值 |

**它的核心优势无法直接复制**：一个 TextView 的 `setText` 只触发一次 native 文本 layout，
而 Compose 的 `Markdown()` 把每个 Markdown 节点变成一个 composable。所以"借"的方式是
**在 Compose 侧手工造出那个"单节点"形态**。

## 二、本轮的方向依据，以及它后来被部分推翻

改造时的依据是 `inbox/2026-09-19-streaming-pace-must-be-time-not-frames.md`：
`Recomposer:recompose` 0.08ms/帧、`postAndWait` 3.9ms/帧 → 瓶颈在
「**每秒画多少次 × 每次画多少**」。于是两个方向都冲这个乘积：

- **每次画多少** → 轻量路径（组件树塌成一两个文本节点）
- **每秒画多少次** → 自适应降频（观感损失用渐显补偿）

> ⚠️ **2026-09-20 更正**：一份新的 Janky frames 数据显示 **GPU 只占 0.6~1.7ms/帧**，
> 绝大部分耗时在 `Application`（2.8~25.9ms）与 `Composition`（8.3~15.0ms）。
> 那张卡「瓶颈在绘制/GPU 侧」的归因**不成立**，已在其开头标注。
>
> 对本卡的影响：
> - **轻量路径的方向仍然正确**，但它省的不是 GPU，而是 UI 线程的重组/测量/绘制录制；
> - **降频的收益被高估了**：GPU 不紧张，少画几次省下的是主线程工作而非 GPU 吞吐；
> - 新数据里慢帧只有 6 帧 / 7 秒，而出字是 31 次/秒，**所以大多数出字帧并不掉帧**，
>   掉的是频率约 1~2 次/秒的另一类事件——量级上与「又写完一块」吻合（见第十一节）。

`StreamingMarkdown.blocks` / `PartialMarkdown.sanitize` 本轮没重写算法（实测 1916 字 62μs/次，
移动端估 0.3~0.6ms/节拍），但**切分的合并策略改了**，原因见第十一节。

## 三、轻量路径省掉了什么

走 `Markdown()` 渲染一个段落块的真实结构：
`CompositionLocalProvider(11 个 local) → Column → Spacer → MarkdownParagraph → MarkdownText`。
库源码（`0.34.0` sources jar）里这三处每次重组都重做，且**无法从外部规避**：

- `MarkdownParagraph.kt`：`buildAnnotatedString { … }` 没有 `remember`，每次重组遍历 AST 子树
  重建整段 `AnnotatedString` 再重新 measure；
- `elements/MarkdownText.kt`：每个文本节点挂 `onPlaced` + `rememberMarkdownImageState` +
  一个 `derivedStateOf`（只为支持图片占位）；`inlineContent` map 每次重建；
- `MarkdownList.kt`：一个列表项是 `Column → Row → Box → BasicText(bullet)` 外加
  `Column → MarkdownElement → MarkdownParagraph`，**一行文字五层布局**。

轻量路径把这些换成：一次显式解析（结果缓存）+ 一两个 `BasicText`。

## 四、视觉一致性是怎么保证的（这是最大的风险点）

两条铁律，改动时不能破：

1. **不自己写行内 Markdown 解析。** 直接调库的
   `String.buildMarkdownAnnotatedString(textNode, style, annotatorSettings)`——
   就是 `MarkdownParagraph` 内部用的同一个函数，且**不是 `@Composable`**，可以自由调用、
   也可以在 JVM 单测里调用。于是行内标记处理逐字符同源。
   容易踩的几个点（都有单测锁住）：
   - 行内代码 **两侧各补一个空格**（`AnnotatedStringKtx` 的 CODE_SPAN 分支）
   - 段落内换行折叠成空格、连续空白折叠成一个空格、行首空白丢弃
   - 标题正文取 `ATX_CONTENT` 子节点，前导空格由 annotator 吃掉
   - 无序 bullet 固定 `"• "`；有序用 CommonMark start number（首项的数字），所以
     `3. xxx` 单独成块仍显示 3
2. **块级判定不用正则猜，用 AST。** 要求「有意义的顶层节点恰好一个，且是 PARAGRAPH / ATX_1..6 /
   单项列表」。代码块、表格、引用、水平线、HTML 块、多段落、嵌套列表、任务列表因此**自动**回退，
   不需要逐条枚举。额外排除图片（`appendInlineContent` 需要 Text 提供同名 inlineContent，
   缺了会抛异常）与链接（`withLink` 依赖 uriHandler 与引用定义表）。

留白是**逐 dp 复刻**的，不是重新设计：

- 库在**每个**顶层节点前插 `Spacer(padding.block)`，**连不产出内容的 EOL 也算一个**。
  所以 `Block` 要记 `leadingBreaks` / `trailingBreaks`：同一块从流式（末尾还没换行）切到定稿
  （多出换行）时，间距必须跟着变，否则会跳。
- 列表项的 `listItemTop` / `listItemBottom` 库加在 `Row` 上；`padding.list` 项目已设为 0。
- `MarkdownBulletList` 给 bullet 多加一份 `listItemBottom`，`MarkdownOrderedList` 没有——
  这处不对称也复刻了（`Block.markerBottomPadding`）。
- 用 `BasicText` 不用 Material3 `Text`：库内部的 `MarkdownBasicText` 就是 `BasicText`，
  不读 `LocalTextStyle`。用 `Text` 会先与主题默认 TextStyle merge，同一段文字两条路径就会有差异。

⚠️ **`chatMarkdownConfig()` 改动 `markdownPadding(...)` 的任何参数，`MarkdownText.kt` 里的
`MARKDOWN_BLOCK_SPACING` / `LIST_ITEM_TOP` / `LIST_ITEM_BOTTOM` 要跟着改。** 这是一份手工镜像，
编译器不会提醒。

## 五、度量（JVM，样本为程序生成的典型 AI 回答）

样本：重复的 `## 标题` + 含 `**加粗**`/行内代码的中文段落 + 三项列表，每 3 轮一个 kotlin 代码块、
每 4 轮一张表格。按出字节拍（步长 4 字符）逐前缀统计**尾块**走轻量路径的比例：

| 全文长度 | 节拍数 | 轻量命中 | 未命中构成 |
|---|---|---|---|
| 268 | 67 | 28% | 标题 47（实为"整篇一块"，见下） |
| 935 | 234 | 64% | 列表项 31、标题 47、表格 5 |
| 1916 | 479 | **73%** | 列表项 64、标题 47、表格 17 |

只支持段落时命中率是 45%，加上标题与单列表项后到 73%。

未命中的两类都是**短文本、成本本来就低**的情形，所以没继续扩展：

- `blocks()` 的 `MIN_SPLIT_LENGTH = 200`：回答开头 200 字符内整篇是一块（标题+段落混在一起）。
  上表的"标题 47"全是这种，不是标题支持不到。
- 列表项刚起头：`isListItemStart` 要求标记后有实际内容，所以 `"…\n- "` 这一两个节拍里
  尾块是「上一项 + 空的新项」。刻意不支持多项块——支持了下一个节拍就会切开变单项，反而闪。

单次同步解析耗时（桌面 JVM，2000 次预热 + 2000~5000 次迭代）：

| 尾块长度 | 单次 |
|---|---|
| 60~120 字符 | 16~36μs |
| 810 字符（退化成"整篇一段"的长段落） | 25~28μs |

这就是**轻量路径选择同步解析、不走后台 conflate** 的依据：几十微秒换来"文字立刻出现、
不闪空白、不落后于打字机"，比一次跨线程往返划算。移动端按慢 5~10 倍估算约 0.1~0.3ms/节拍。

⚠️ 度量脚本是临时文件，跑完即删（未入库）。要重跑得按上面描述重建。

### 尾块与固化块是两个度量对象，必须都看

> ⚠️ **2026-09-20 重写。** 本节第一版的规避规则是「度量对象必须是尾块，不是 `blocks(全文)`
> 的块列表」。**那条规则是错的，而且正是漏掉第十一节那个真问题的直接原因。**
> 当时看到固化块命中率只有 10%，我判定"度量对象选错了"就把它丢掉了——
> 而那 10% 恰恰是 `mergeShortBlocks` 与轻量路径互相抵消的信号，直到用户反馈"表现依旧不好"
> 才回头查出来。

两个对象回答的是**不同的问题**，都要量：

| 度量对象 | 怎么取 | 回答什么问题 | 对应的帧成本 |
|---|---|---|---|
| **尾块** | 按出字节拍逐前缀取 `blocks(sanitize(prefix)).last()` | 高频重组那一个 item 贵不贵 | 每次出字（15~31 次/秒） |
| **固化块** | `blocks(定稿全文)` 的整个列表 | 新块首次进入列表时贵不贵 | 每写完一块（1~2 次/秒） |

两者形态完全不同，所以数值天然会差：尾块永远独占一组、从不参与合并；
固化块则被 `mergeShortBlocks` 加工过。

**规避规则：看到两个对象的数值差一个数量级，不要假定其中一个「量错了」，
先把差异的来源解释清楚。** 差异本身往往就是问题所在——本例中就是。

更一般的一条（本轮真正起作用的推理）：**`Janky frames` 里慢帧的「频率」能指认事件类别。**
6 帧 janky / 7 秒 ≈ 1 次/秒，对得上"每写完一块"，对不上"每次出字"（31 次/秒）。
先用频率锁定是哪一类事件，再去量那一类的成本，比直接猜哪段代码慢可靠得多。

顺带一句：`ChatViewModel.warmBlocks` 之所以要两条缓存都试，也是因为
预热面对的是固化块、UI 高频面对的是尾块，两者走的渲染路径可能不同。

## 六、自适应降频的公式与不变式

```
出字间隔 = clamp(实测帧间隔 × 2, 32ms, 64ms)
```

| 实测帧间隔 | 出字间隔 | 出字频率 |
|---|---|---|
| 8.3ms（120Hz 正常） | 32ms | ~31/s（与改造前一致） |
| 16.7ms（60Hz 正常） | 33ms | ~30/s（与改造前一致） |
| 33ms（掉到 30fps） | 64ms | ~15/s |
| ≥40ms | 64ms（封顶） | ~15/s |

它修掉的退化情形：帧间隔被拖长到接近目标间隔时，`framesPerEmit` 会被压到 1，
**等于每一帧都在出字**——设备越慢反而越贪心。核心不变式是「任何帧间隔下都不该每帧出字」，
由 `TypewriterPaceTest` 守。

公式**无状态**：帧间隔恢复后自然回落，不需要计数器/衰减，也不会卡在降频档位。
上限 64ms 是观感底线（约 15 次/秒）。

注意 `frameIntervalNanos` 只在 `MIN_FRAME_NANOS..MAX_FRAME_NANOS`（4~40ms）内更新，
所以能学到的最大帧间隔是 40ms，正好把间隔推到上限。

## 七、顺手修掉的一个功能性 bug（不是性能问题）

`StreamingTextBuffer` 原来的静默判定是
`sb.contains("invoke") || sb.contains("tool_calls")`（忽略大小写），**裸词就触发**。
而这是个面经 App，"反射 `Method.invoke`"、"function calling 的 `tool_calls` 字段"
正是高频考点正文。命中后不报错，表现是：转圈很久 → 整段答案突然出现 → 打字机因积压巨大
触发加速追赶 → 掉帧尖峰。判定已收紧到必须是标签形态（`</?\s*[\w.:-]*(invoke|tool_calls)\b`），
与 `InlineToolCallParser.INVOKE` 的匹配前提一致。

同时把判定与 `safeEmitEnd()` 改成增量：原来每个 chunk 都对整个 buffer 重扫，
chunk 到达 20~50 次/秒、长回答上千个 chunk，累计 O(n²) 次字符比较压在 SSE 的 IO 线程上。
现在只扫新增窗口（尾部保留 32 字符重叠区，防标记被 chunk 边界切开），
「最后一个悬空 `<`」的位置增量维护。重叠区是必须的，`StreamingTextBufferTest` 有
「逐字符喂入」与「跨 chunk 边界」两条用例守着。

## 八、其它改动

- `ChatViewModel.PUBLISH_INTERVAL_NANOS` 16ms → 32ms，对齐打字机的**基准**出字间隔
  （不跟自适应一起抬：发布快于消费只是浪费，慢于消费会让文字发涩）。省下的是每次
  `builder.toString()` 的全量拷贝与同规模垃圾——低端机上这份 GC 压力本身就是掉帧来源。
- `ChatViewModel.warmBlocks` 现在先试 `InlineMarkdown.annotate(block)`，返回 null 才
  `MarkdownStateCache.warm(block)`。两条路径两套缓存，预热必须走对应那条，否则 UI 首次组合
  还是要同步解析；`annotate` 顺便把"这块不是单块"的判定也缓存下来，UI 侧连判定都省了。
- `MarkdownText` 里把完整渲染拆成 `FullMarkdownText`，让流式尾块的回退不必重做一次轻量判定
  （判定含一次解析，尾块每节拍都是新文本、缓存命不中，白算就是白花几十微秒）。

## 九、未验证 / 下一个落点

**未验证**：

1. 没有真机帧数据。需要在 Honor 90 Pro（120Hz，帧预算 **8.33ms**）与一台低端机上各抓一次
   system trace，看 `postAndWait` 是否随出字频率下降而下降。
2. 渐显的视觉效果只在逻辑层验证（span 区间与 alpha 单调性），**没有真机目视确认**。
   末尾一个字符 alpha 约 0.09，需确认在不同亮度下不会显得"缺字"。
3. 轻量路径与完整渲染器的视觉一致性由单测锁住了**文本内容与留白换算**，
   但没有截图对比。字重/字距这类只有目视能发现。

**下一个落点**（按性价比）：

1. 真机测完再决定 `MAX_EMIT_INTERVAL_MS`（64ms）合不合适。
2. `FADE_TAIL_CHARS = 10` 是照搬 FluidMarkdown 的取值，没调过。降频后单次推进字数变多，
   渐显窗口可能需要跟着出字步长动态调整。
3. 代码块与表格仍走完整渲染器（`PartialMarkdown` 的按行揭示是它们唯一的保护）。
   若真机 trace 显示代码块尾块仍是热点，可以考虑给 `CodeCard` 也做一条轻量路径——
   它本来就是自定义组件，不受库结构约束。

## 十、这类改动能在 JVM 单测里验到多少（比预期多）

`app/src/test` 的 JVM 单测**可以直接用 `AnnotatedString` / `SpanStyle` / `TextStyle` / `Color`**，
不需要 Robolectric、也不必挪到 instrumented test：这些类型不依赖 Android framework，
inline class（`Color` / `TextUnit`）与 `TextStyle.toSpanStyle()` 都是纯 JVM 逻辑。

更有用的一条：**渲染库把行内 annotator 暴露成了非 `@Composable` 的公开函数**
（`String.buildMarkdownAnnotatedString(textNode, style, annotatorSettings)`，配套的
`DefaultAnnotatorSettings` 是普通 class，可直接 new）。于是「库会把这段 Markdown 渲染成什么文本」
这个问题可以在 JVM 单测里**直接问库本身**，不必靠目视截图比对。

本轮因此把一批原本只能靠眼睛发现的东西变成了可回归的断言：行内代码两侧补空格、
段落内换行折叠、标题正文取 `ATX_CONTENT`、有序项的 CommonMark start number、
块级留白随尾随换行数变化。**库升级改掉任何一条，`InlineMarkdownTest` 会先炸，
而不是等用户看到文字位移。**

顺带修正一个既有提案的路线：`inbox/2026-09-19-markdown-renderer-streaming-perf.md` 结尾把轻量路径
设想成「纯段落时不走 Markdown 解析器，自己 remember 住 AnnotatedString」。本轮落地时选了相反的做法——
**不自研解析，改调库的 annotator**。理由是解析本来就只有几十微秒（第五节的数据），自研省不到什么，
却要自己复刻上面那一串行为，视觉偏差的风险远大于收益。

仍然只能靠真机的：字重/字距的细微差异、渐显的实际观感、帧耗时。见第九节。

## 十一、`mergeShortBlocks` 与轻量路径互相抵消（改造的第二轮）

### 现象

第一轮改完后帧表现仍然不好。度量发现：**固化块的轻量路径命中率只有 10%**，
而按空行粗切时是 54%。尾块命中 73%，固化块 10%——差距不合理。

### 根因

`StreamingMarkdown.mergeShortBlocks` 会把相邻短块贪心并到 `MIN_BLOCK_LENGTH = 80` 以上。
于是「标题 + 段落 + 列表项」被粘成一个混合块，而混合块**丧失轻量路径资格**
（`InlineMarkdown` 要求顶层恰好一个支持的节点），被迫走完整渲染器。

合并的原始动机是「每块一个 `Markdown()` 实例 + 一整套渲染配置对象，块多就是固定开销」。
**轻量路径把这个动机消掉了**：单一形态的块只渲染成一两个 `BasicText`，多几十块无所谓。
于是合并从优化变成了倒扣。

**这是两个优化互相抵消，不是任何一方写错了。** 第一轮没发现，因为当时只度量了尾块
（尾块永远独占一组、从不参与合并，所以完全看不出这个问题）。

### 规避规则

合并的判据从「块够不够长」改为「**这一块自己能不能走轻量路径**」：

- `InlineMarkdown.looksSingleForm(block)` 为真 → 保持独立；
- 为假（引用、水平线、setext 标题这类碎块）→ 照旧贪心合并。

判定必须是**不解析的粗判**：`mergeShortBlocks` 在 `blocks()` 里、每个出字节拍都跑一遍，
放一次真解析进去就是「每块几十微秒 × 上百块 = 每帧几毫秒」。判错的代价可控——
只影响合并决策与命中率，不影响渲染正确性，所以粗判偏保守（拿不准返回 false）。

### 效果（JVM 度量，样本同第五节）

| | 块数 | 固化块轻量命中 |
|---|---|---|
| 无条件合并（改前） | 19 | 2（**10%**） |
| 按形态合并（改后） | 63 | 56（**88%**） |

块数从 19 涨到 63，但其中 56 块只是一两个 `BasicText`。尾块命中率不受影响（仍 73%）。

### 连带的契约变更

`StreamingMarkdownTest` 原来有一条「合并短块后块数明显下降」，它表达的是**旧动机**，
已替换为「能轻量渲染的块不被合并拖下水」（守命中率 ≥ 80%）+「非单一形态的碎块仍然合并」+
「最后一块独立」。**块数不再是优化目标**，别再加回块数上限的断言。

## 十二、这次装上了 system trace 打点（`FrameTrace.kt`）

归因错过一次的直接原因是：Studio trace 里应用代码全塌在 `Choreographer#doFrame` 下面，
只能看到汇总，看不出层次，只好靠"频率吻合"去推断。

现在链路上有 5 个 section（`om.emit` / `om.blocks` / `om.annotate` / `om.fade` /
`om.parseBlocking`），抓一次 trace 就能直接读出出字帧里各阶段的耗时。

两个设计要点：

- `Trace.beginSection` 在没抓 trace 时由 framework 检查 atrace tag 后直接返回（几十纳秒），
  **所以不需要构建变体或开关保护，长期留在代码里即可**。需要时抓一次就有，
  比"怀疑哪里慢再加打点重新编包"快得多。
- label 必须是**常量字符串**，拼接会每次分配。
- `om.emit` 只包住「写 `displayLen`」这一下（重组发生在本帧更晚阶段，包不进来）。
  它的作用是在 trace 上标出**哪些帧在出字**，用来判断慢帧与出字是否相关——
  这正是这次最缺的那个信息。

⚠️ `om.parseBlocking` 出现在流式期间就说明**有块没被预热、正在主线程同步解析**。
目前已知的缺口：流式期间新产生的固化块没有预热机会（`warmBlocks` 只在定稿后跑），
走完整渲染器的那 12%（代码块、表格）会在首次进入列表时同步解析。尚未修。

## 十三、两个必须先确认的前提（否则测出来的数都不可用）

1. **构建变体未确认。** trace 里进程名是 `k.offermate.dev`，而按 `app/build.gradle`，
   `debug` / `profileable` / `nonMinifiedRelease` / `benchmarkRelease` **共用**
   `com.jk.offermate.dev` 这个 applicationId，所以**从包名分不出是哪个变体**。
   若是 debug 包，Compose 的帧耗时比 release 差一截（ART 优化被关、R8 未跑），
   结论不可用——项目专门建 `profileable` 变体就是为这个，见 `app/build.gradle` 里的长注释。
   同一个坑这是第二次：`streaming-pace-must-be-time-not-frames.md` 也写了"构建变体未确认"。
   **建议：抓 trace 前先 `adb shell dumpsys package com.jk.offermate.dev | grep -i flags`
   确认没有 DEBUGGABLE。**
2. **baseline profile 里没有任何应用自己的代码**（merged profile 5446 行，
   `grep -c offermate` 为 **0**），所以应用侧的 composable 与整条流式渲染链路全靠 JIT，
   而抓 trace 最容易采到的就是 JIT 还没追上的那几秒。
   **在补上之前，profileable 包的帧数据会系统性偏悲观**，不能用来判断某段渲染代码贵不贵。

   现象、影响、待查的根因方向与验收判据单独记在
   `inbox/2026-09-20-baseline-profile-has-no-app-rules.md`——它影响启动与首屏，
   不只是流式。

## 十四、`Composition` 持续 8~15ms 是一个独立的、尚未归因的问题

新数据里 6 帧的 `Composition` 全在 8.29~14.97ms，包括那两帧 `Application` 只有
2.8ms / 4.1ms 的。也就是说**它与出字频率无关，是每帧的固定成本**。

一个应用窗口 + 系统栏正常是 1~3ms。8~15ms 说明 SurfaceFlinger 大概在做 GPU 合成而非 DPU overlay。

代码里的可疑面（**未验证，仅列出以便下次排查**）：多处半透明悬浮层叠在滚动内容上方，
且都带较大投影——`FollowUpScreen` 的底部 dock（`surface.copy(alpha = 0.82f)` +
`shadowElevation = 8.dp`）、"更新答案"浮条（`alpha = 0.82f` + `6.dp`）、
`BottomTabs`（`alpha = 0.82f` + `12.dp`），叠加 `enableEdgeToEdge()`。

**排查顺序**：先用 `Debug GPU Overdraw` 看叠加层数，再看 `adb shell dumpsys SurfaceFlinger`
里该帧是否有 layer 落到 GPU composition。这条与流式渲染是**两个独立问题**，
不要混在一起改。

## 证据

- 库源码：`com.mikepenz:multiplatform-markdown-renderer:0.34.0` sources jar
  （`~/.gradle/caches/modules-2/files-2.1/com.mikepenz/…-0.34.0-sources.jar`），
  核对过 `compose/Markdown.kt`、`compose/MarkdownExtension.kt`（每个节点前的 Spacer）、
  `compose/elements/MarkdownParagraph.kt`、`compose/elements/MarkdownText.kt`、
  `compose/elements/MarkdownList.kt`、`compose/ComposeLocal.kt`（默认 BulletHandler）、
  `annotator/AnnotatedStringKtx.kt`（行内样式规则）、`model/MarkdownPadding.kt`（默认值）。
- FluidMarkdown 源码：`Android/AntFluid/fluid-markdown/src/main/java/com/fluid/afm/markdown/widget/PrinterMarkDownTextView.java`
  （`printing` / `handleSpan` / `gradiantColorAnimateText`）、`markdown/span/OpacitySpan.java`。
- 新增单测：`InlineMarkdownTest`（14 条）、`StreamingTextBufferTest`（9 条）、
  `TypewriterPaceTest`（4 条）；`StreamingMarkdownTest` 的合并契约已按第十一节重写。
  `./gradlew assembleDebug :app:testDebugUnitTest` 全绿（268 条）。
- 命中率与耗时数据来自临时度量脚本的两次运行（脚本已删，见第五节与第十一节）。
- 推翻旧归因的 Janky frames 数据：用户提供的 Android Studio Profiler 截图（System Trace，
  6 个 janky 帧的 Frame Duration / Application / GPU / Composition 四列），
  已转录进 `inbox/2026-09-19-streaming-pace-must-be-time-not-frames.md` 开头的更正块。
  **该 trace 的构建变体仍未确认**，见第十三节。
