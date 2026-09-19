---
trust: L2
anchors:
  - app/src/main/java/com/jk/offermate/ui/components/MarkdownText.kt#MarkdownContent
  - app/src/main/java/com/jk/offermate/ui/components/StreamingMarkdown.kt
  - app/src/main/java/com/jk/offermate/ui/components/PartialMarkdown.kt
verified_commit: 0e7f3fb
verified_at: 2026-09-19
supersedes: null
---

# 流式 Markdown 的性能瓶颈在渲染库的重组形态，不在自研的切块/补全逻辑

排查"流式输出仍不够流畅"时的两条归因结论。**本卡只做了源码核对与 JVM 基准，没有做真机帧率测量**，性能影响的量级是推断值。

## 一、`Markdown()` 的配置参数每次组合都是新实例，导致整块后代无条件重组

### 现象

`MarkdownText` 已经把解析结果用 `remember(current) { ParsedMarkdown(current) }` 稳成 `@Immutable` 参数，
但只要 `MarkdownContent` 重组一次（流式尾块每个出字节拍都会），整块 Markdown 的后代节点就全部重建，
而不只是内容真正变化的那个节点。

### 归因

> **2026-09-19 补充（本卡首版写得偏轻）**：其中 4 个 local 是 `staticCompositionLocalOf`，
> 机制比"读取点失效"严重得多——见下面「静态 local」一节。修复已落地，见文末「修复」。

`multiplatform-markdown-renderer 0.34.0` 的 `Markdown()` 把 11 个配置值塞进一个 `CompositionLocalProvider`：

```
LocalReferenceLinkHandler, LocalMarkdownPadding, LocalMarkdownDimens,
LocalMarkdownColors, LocalMarkdownTypography, LocalImageTransformer,
LocalMarkdownAnnotator, LocalMarkdownExtendedSpans, LocalMarkdownInlineContent,
LocalMarkdownComponents, LocalMarkdownAnimations
```

而这些值的实现类（`DefaultMarkdownColors` / `DefaultMarkdownTypography` / `DefaultMarkdownPadding` /
`DefaultMarkdownAnimation` / `DefaultMarkdownComponents`）都是**普通 `class`，不是 `data class`**。
它们标了 `@Immutable`／`@Stable`，但 Compose 判断参数是否变化用的是 `equals`，普通 class 的 `equals` 就是引用相等。

于是：

- `MarkdownContent` 里的 `markdownColor(...)` / `rememberChatMarkdownTypography()` / `markdownPadding(...)` /
  `markdownAnimations(...)` / `markdownComponents(...)` 每次组合都返回新实例；
- 另外 5 个（`dimens` / `imageTransformer` / `annotator` / `extendedSpans` / `inlineContent`）走的是
  `Markdown()` 的**默认参数表达式**，不显式传的话同样每次组合新建；
- 11 个 CompositionLocal 全部被判为"变了"→ 所有读取它们的后代（`MarkdownParagraph`、`MarkdownText`、
  列表、表格）无条件重组。

⚠️ `rememberChatMarkdownTypography()` 函数名带 `remember`，但**函数体里没有 `remember`**，
源码注释也承认"每次重组只剩一个轻量包装对象的分配"——当时低估了它，这个"轻量分配"的真实代价是让
下游 CompositionLocal 失效。看到 `remember` 前缀不要假设它真的记忆化了。
（已改名为 `chatMarkdownTypography`，去掉误导性前缀。）

### 静态 local：为什么代价是"整块无条件重建"

`ComposeLocal.kt` 里这 4 个是 **`staticCompositionLocalOf`**：

| local | 本项目传的值 | 是否每帧新实例（改之前） |
|---|---|---|
| `LocalMarkdownPadding` | `markdownPadding(list = 0.dp)` | 是 |
| `LocalImageTransformer` | 默认 `NoOpImageTransformerImpl()` | 是 |
| `LocalMarkdownInlineContent` | 默认 `markdownInlineContent()` | 是 |
| `LocalReferenceLinkHandler` | `state.referenceLinkHandler` | 随 state 稳定；但 `State.Loading()` 的默认值每次新建 |

静态 CompositionLocal **不记录读取点**：值一变，Compose 直接把 provider 的整个 content 子树
标记为待重组，跳过机制完全不起作用。所以这不是"读 typography 的节点失效"，
而是**整块 Markdown 的所有节点无条件重建**。

剩下 6 个（`colors` / `typography` / `dimens` / `annotator` / `extendedSpans` / `components`）走
`compositionLocalOf`，只失效真正读它们的后代——但 Markdown 的每个元素都读 typography 和 colors，
实际范围差不多。

### 修复

`MarkdownText.kt`：新增 `ChatMarkdownConfig` 把 7 个 `@Composable` 工厂的结果用
**无 key 的 `remember`** 锁住首批实例；`markdownComponents` / `markdownAnnotator` /
`NoOpImageTransformerImpl` / `State.Loading` 不是 `@Composable`，直接提成顶层单例。
调用 `Markdown()` 时**10 个参数全部显式传**。

两个必须记住的点：

1. **漏传任何一个参数就等于没改** —— 它会落回默认参数表达式，每次组合新建实例。
2. 复用首批实例的前提是这些值不依赖组合环境。当前 `OfferMateTheme` 固定浅色（`darkTheme`
   参数被忽略），`markdownColor` 只从主题读 `onBackground` / `outlineVariant`，都是常量。
   **日后加深色模式，这里要改成 `remember(MaterialTheme.colorScheme) { … }`**，否则切换主题后
   Markdown 颜色不跟着变。

### 同源的另两处库层限制

- `MarkdownSuccess` 是 `state.node.children.forEach { MarkdownElement(...) }`，**没有 `key()`**。
  流式尾块的 AST 节点数会增长，Compose 按位置识别 slot，节点插入会让后面的 slot 全部重建。
- `MarkdownParagraph` 与 `MarkdownText(content, node, style, ...)` 里的 `buildAnnotatedString { ... }`
  **没有 `remember`**，每次重组遍历 AST 子树重建整段 `AnnotatedString` 再重新 measure。
  并且 `annotatorSettings: AnnotatorSettings = annotatorSettings()` 又是一个每次新建的默认参数。

这两处**没有规避**，仍然是流式尾块每次更新的固有成本。根本解法是给正在生成的那个块另开一条
轻量渲染路径（纯段落时不走 Markdown 解析器，自己 `remember` 住 `AnnotatedString`），
属于未落地的改造提案，本卡不记录。

### 证据

- 库源码 `com.mikepenz:multiplatform-markdown-renderer:0.34.0` 的 sources jar：
  - `commonMain/com/mikepenz/markdown/compose/Markdown.kt` 约 195–226 行：默认参数列表 + `CompositionLocalProvider`
  - `commonMain/com/mikepenz/markdown/compose/Markdown.kt` 约 236–246 行：`MarkdownSuccess` 的无 key `forEach`
  - `commonMain/com/mikepenz/markdown/model/MarkdownColors.kt` 42 行：`@Immutable class DefaultMarkdownColors`
  - 同理 `MarkdownTypography.kt` 30 行、`MarkdownPadding.kt` 30 行、`MarkdownAnimations.kt` 18 行、
    `compose/components/MarkdownComponents.kt` 130 行 `private class DefaultMarkdownComponents`
  - `commonMain/com/mikepenz/markdown/compose/elements/MarkdownParagraph.kt`：无 remember 的 `buildAnnotatedString`
- 复现方式：`unzip` 上述 sources jar 后 grep `CompositionLocalProvider` / `data class`。
- **未做真机验证**：没有 Macrobenchmark 帧耗时数据支撑"这是主要瓶颈"，只确认了机制成立。

## 二、`StreamingMarkdown.blocks()` 与 `PartialMarkdown.sanitize()` 不是瓶颈，别再优化它们的算法

### 数据

临时 JVM 基准（桌面 JVM，2000 次迭代、200 次预热），样本为程序生成的典型 AI 回答
（重复的 `## 标题` + 含 `**加粗**` 和行内代码的中文段落 + 三项列表，每三轮插一个 kotlin 代码块）：

| 全文长度 | 切出块数 | `blocks()` | `sanitize()` | 块 equals | 合计/帧 |
|---|---|---|---|---|---|
| 672 | 25 | 23μs | 7μs | 1μs | 31μs |
| 1528 | 55 | 41μs | 12μs | 1μs | 55μs |
| 3020 | 105 | 50μs | 10μs | 1μs | 61μs |
| 6079 | 210 | 62μs | 21μs | 1μs | 84μs |

3000 字全文 61μs／帧，按移动端慢 5–10 倍估算约 0.3–0.8ms，占 16ms 帧预算 2–5%。

即便打字机每帧都对全文重跑这两个函数（当前就是这样），也不构成主要开销。
**排查流式卡顿时不要从这两个纯逻辑函数入手。**

### 顺带记录的一个事实（表中块数已过时）

表里的块数是**加合并之前**的：`blocks()` 切得很碎，3000 字 → 105 块、6000 字 → 210 块。
这是 `f77f1fe`／`38a2c26` 按列表项切开的预期结果（为了让 LazyColumn 只组合可见块），
但也意味着一条长消息会有上百个 `Markdown()` 实例，每个都带一套上文所说的配置对象。

现已加 `mergeShortBlocks`（`MIN_BLOCK_LENGTH = 80`，贪心从前合并，**最后一块永远独占一组**
以保证流式尾块最小），3000 字降到 40 块以下。耗时数据不受影响（那几十 μs 本来就不是瓶颈）。

### 改 `blocks()` 时必须知道的两条契约

1. `blocks(text).joinToString("") == text` —— 切分绝不能改动内容。
2. **前缀稳定性的 slack 是 2**，不是 1。末尾两个元素本来就还在变：正在生成的原始块，
   以及还没攒够 `MIN_BLOCK_LENGTH`、仍在吸收后续块的那个合并组。已定稿块必须逐字稳定，
   否则 `MarkdownStateCache` 全程命中不上。
3. 验证前缀稳定性**必须先过 `PartialMarkdown.sanitize`**（这就是线上链路）。直接切裸的流式文本
   会踩到"未闭合围栏 → 整篇返回一块"，边界自然对不上——第一版测试就是这么写错的。

三条都由 `StreamingMarkdownTest` 覆盖。

### 证据（基准）

基准文件为临时创建、跑完即删（未入库），表中数据来自
`./gradlew :app:testDebugUnitTest --tests "...TmpStreamBench" --rerun-tasks` 的一次运行输出。
要重跑就按上面描述的样本构造方式重建一个临时测试。

## 附：knowledge base 缺口

`docs/kb/map/` 目前只有 `import-pipeline` / `quiz-puzzle` / `agent-tools` 三张卡，
**AI 对话的流式渲染链路没有代码地图**。这条链路跨 8 个文件、有 4 轮优化史
（`887d571` / `f77f1fe` / `dc7112a` / `38a2c26`），每次改动都得重新把链路读一遍。
建议审阅时考虑补一张 `map/chat-streaming.md`。
