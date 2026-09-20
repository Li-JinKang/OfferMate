package com.jk.offermate.ui.components

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import com.mikepenz.markdown.annotator.DefaultAnnotatorSettings
import com.mikepenz.markdown.annotator.buildMarkdownAnnotatedString
import com.mikepenz.markdown.model.markdownAnnotator
import com.mikepenz.markdown.utils.getUnescapedTextInNode
import org.intellij.markdown.MarkdownElementTypes
import org.intellij.markdown.MarkdownTokenTypes
import org.intellij.markdown.ast.ASTNode
import org.intellij.markdown.ast.findChildOfType
import org.intellij.markdown.flavours.gfm.GFMFlavourDescriptor
import org.intellij.markdown.flavours.gfm.GFMTokenTypes
import org.intellij.markdown.parser.MarkdownParser

/**
 * **单块内容的轻量渲染路径**：把一个块直接算成 [AnnotatedString]，交给一两个 `BasicText` 渲染，
 * 完全绕开 `Markdown()` 的组件树。覆盖段落、标题、单个列表项——按块切分后，
 * AI 回答里绝大多数块都是这三种形态。
 *
 * ## 为什么值得单开一条路
 *
 * 走 `Markdown()` 渲染一个段落块，实际结构是
 * `CompositionLocalProvider(11 个 local) → Column → Spacer → MarkdownParagraph → MarkdownText`，
 * 而最里面那层每次重组都做三件不必要的事（均为库实现，无法从外部规避）：
 * 1. `MarkdownParagraph` 的 `buildAnnotatedString { … }` **没有 remember**，每次重组都重新遍历
 *    AST 子树重建整段 `AnnotatedString`，紧接着重新 measure；
 * 2. `MarkdownText` 给每个文本节点挂 `onPlaced` 回调 + `rememberMarkdownImageState` +
 *    一个 `derivedStateOf`，只为了支持图片占位；
 * 3. `inlineContent` map 每次重建。
 *
 * 列表项更重：`Column → Row → Box → BasicText(bullet)` 再加 `Column → MarkdownElement → …`，
 * 一行文字五层布局。
 *
 * 流式尾块每个出字节拍都会重组，上面这些就一直挂在主线程上。换成一两个 `BasicText` 之后，
 * 一个节拍的工作量是「一次解析（在这里显式做掉）+ 文本节点的 measure/draw」。
 *
 * 思路取自蚂蚁开源的 [FluidMarkdown](https://github.com/antgroup/FluidMarkdown)：它的流式渲染单元
 * 始终是**一个 TextView + 一个 Spannable**，出字只做「切片 + 搬 span + setText」，
 * 渲染成本不随 Markdown 结构复杂度增长。Compose 侧对应物就是「一个 BasicText + 一个 AnnotatedString」。
 *
 * ## 视觉一致性怎么保证
 *
 * **不自己写行内 Markdown 解析**，而是直接调用渲染库的 `buildMarkdownAnnotatedString`——
 * 也就是 `MarkdownParagraph` 内部用的同一个函数（它不是 `@Composable`，可以自由调用）。
 * 于是行内标记的处理逐字符一致：`**粗体**`、`*斜体*`、`~~删除~~`、`` `行内代码` ``
 * （库会在行内代码两侧各补一个空格）、段落内换行折叠成空格、转义与实体的还原，全部同源。
 *
 * 块级判定也**不用正则猜**：解析出 AST 后要求「有意义的顶层节点恰好只有一个，且是支持的类型」，
 * 不满足就返回 null 让调用方回退。于是代码块、表格、引用、水平线、HTML 块、多段落、
 * 嵌套列表、任务列表一律自然落回原来的 `Markdown()` 路径，不需要逐条枚举。
 *
 * 另外需要排除的是**图片与链接**：图片走 `appendInlineContent`（需要 Text 提供同名 inlineContent，
 * 缺了会抛异常），链接走 `withLink`（需要 uriHandler 与引用定义表）。这两类也交回 `Markdown()`。
 *
 * 块级留白与列表缩进则是逐 dp 复刻的，见 [Block] 与 `InlineBlockText`。
 */
object InlineMarkdown {

    /** flavour 无状态可共享；parser 每次新建，避免并发共用实例。 */
    private val flavour = GFMFlavourDescriptor()

    /**
     * 行内代码的样式。复刻库的 `MarkdownTypography.codeSpanStyle`：
     * `inlineCode` 叠加 `colors.inlineCodeBackground`（本项目设为透明，见 `chatMarkdownConfig`）。
     * `inlineCodeText` 项目未设置（`Color.Unspecified`），所以颜色沿用 `inlineCode` 自身的。
     */
    private val CodeSpanStyle = InlineCodeStyle.copy(background = Color.Transparent).toSpanStyle()

    /**
     * 传给库的注解设置。链接相关的两项给了值但用不上——含链接的块不会走到这条路径。
     * 是普通 class，可作常量持有。
     */
    private val settings = DefaultAnnotatorSettings(
        linkTextSpanStyle = TextLinkStyles(style = null),
        codeSpanStyle = CodeSpanStyle,
        annotator = markdownAnnotator(),
        referenceLinkHandler = null,
        linkInteractionListener = null
    )

    private const val MAX_ENTRIES = 400

    private class Entry(val block: Block?)

    private val cache = object : LinkedHashMap<String, Entry>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Entry>): Boolean =
            size > MAX_ENTRIES
    }
    private val lock = Any()

    /**
     * 一个可直接渲染的块。
     *
     * [leadingBreaks] / [trailingBreaks] 是块前后的顶层换行数，用来**逐 dp 复刻**
     * `Markdown()` 的块级留白：库在每个顶层节点前插一个 `Spacer(padding.block)`，
     * 连不产出任何内容的 EOL 也算一个。漏掉这份换算，同一块从流式切到定稿时
     * （文本末尾多出换行）间距会跳一下。
     *
     * @property marker 列表项的前导标记（`"• "` / `"3. "`）；非列表项为 null
     * @property markerBottomPadding 无序列表的 bullet 额外带一份 `listItemBottom`（有序的没有），
     *   这是库 `MarkdownBulletList` 与 `MarkdownOrderedList` 的一处不对称，一并复刻
     * @property isHeading 标题要带 `heading()` 语义，供无障碍服务识别
     */
    @Immutable
    class Block(
        val text: AnnotatedString,
        val style: TextStyle,
        val leadingBreaks: Int,
        val trailingBreaks: Int,
        val marker: String? = null,
        val markerBottomPadding: Boolean = false,
        val isHeading: Boolean = false
    ) {
        /** 叠一层尾部渐显，其余渲染参数保持不变。见 [InlineMarkdown.fadeTail]。 */
        fun faded(count: Int = FADE_TAIL_CHARS): Block = Block(
            text = fadeTail(text, style.color, count),
            style = style,
            leadingBreaks = leadingBreaks,
            trailingBreaks = trailingBreaks,
            marker = marker,
            markerBottomPadding = markerBottomPadding,
            isHeading = isHeading
        )
    }

    /**
     * 把 [text] 算成可直接渲染的块；**不是支持的形态则返回 null**，
     * 调用方需回退到完整的 `Markdown()` 渲染。
     *
     * 解析是同步的。这与 [MarkdownStateCache.warm] 的异步策略不同，是刻意的：
     * 单个块的解析只有十几到几十微秒（对比一次跨线程往返 + 一帧延迟），同步做换来
     * 「文字立刻出现、不闪空白、不落后于打字机」。
     *
     * @param store 是否写入缓存。**流式中间态必须传 false**：一条回复会产生上千个递增前缀，
     *   全部入缓存会把真实内容整批挤出去。
     */
    fun annotate(text: String, store: Boolean = true): Block? {
        if (text.isEmpty()) return null
        peekEntry(text)?.let { return it.block }
        val result = traced(TraceLabels.ANNOTATE) { compute(text) }
        if (store) synchronized(lock) { cache[text] = Entry(result) }
        return result
    }

    /**
     * **不解析**的粗判：这一块看起来是不是「单段落 / 单标题 / 单列表项」，也就是
     * [annotate] 有希望接手的形态。
     *
     * 给 [StreamingMarkdown.blocks] 的合并决策用。那里每个出字节拍都要跑一遍，
     * 不能调 [annotate]（含一次真解析，每块几十微秒 × 上百块 = 每帧几毫秒）。
     *
     * 判错的代价是可控的：判 true 而 [annotate] 最终回退，只是少合并了一个块；
     * 判 false 只是多合并一个块。两者都不影响渲染正确性，只影响命中率。
     * 所以这里**偏保守**——拿不准就返回 false。
     */
    fun looksSingleForm(text: String): Boolean {
        val body = text.trimEnd('\n', ' ', '\t')
        if (body.isEmpty()) return false
        // 代码围栏与多段落：annotate 的预筛也是这两条
        if (body.contains("```")) return false
        if (body.contains("\n\n")) return false

        val lines = body.split("\n")
        val first = lines.first()
        if (first.isBlank()) return false
        if (startsBlockLevel(first)) return false
        if (HORIZONTAL_RULE.matches(first.trim())) return false

        // 后续行不得引入第二个块级结构（列表项的缩进续行是允许的）
        for (i in 1 until lines.size) {
            val line = lines[i]
            if (startsBlockLevel(line.trimStart())) return false
            if (SETEXT_UNDERLINE.matches(line.trim())) return false
            // 第 0 列再出现列表标记 = 第二个列表项，那要靠库的 Column 排项间距
            if (TOP_LEVEL_LIST_ITEM.containsMatchIn(line)) return false
        }
        return true
    }

    /** 表格 / 引用 / 缩进代码块：都要完整渲染器。 */
    private fun startsBlockLevel(line: String): Boolean =
        line.startsWith("|") || line.startsWith(">") ||
            line.startsWith("    ") || line.startsWith("\t")

    private val HORIZONTAL_RULE = Regex("^(-{3,}|\\*{3,}|_{3,})$")
    private val SETEXT_UNDERLINE = Regex("^(={2,}|-{2,})$")
    private val TOP_LEVEL_LIST_ITEM = Regex("^([-*+]|\\d+[.)])\\s+\\S")

    /** 同步读缓存；命中返回包装（其中 `block == null` 表示「已判定为不支持」）。 */
    private fun peekEntry(text: String): Entry? = synchronized(lock) { cache[text] }

    private fun compute(text: String): Block? {
        // 便宜的预筛：命中就肯定不是单块，省掉一次解析。
        if (text.contains("```")) return null
        if (text.trimEnd('\n', ' ', '\t').contains("\n\n")) return null

        val tree = try {
            MarkdownParser(flavour).buildMarkdownTreeFromString(text)
        } catch (error: Throwable) {
            // 解析异常时回退到 Markdown() 路径，让它去处理（那边有 State.Error 分支）。
            return null
        }

        // 顶层只允许一个有渲染产出的节点，外加行分隔与空白。
        // 「结构复杂就回退」这条规则落在这里，不必逐条枚举代码块/表格/引用/HTML。
        var node: ASTNode? = null
        var leading = 0
        var trailing = 0
        for (child in tree.children) {
            when (child.type) {
                MarkdownTokenTypes.EOL -> if (node == null) leading++ else trailing++
                MarkdownTokenTypes.WHITE_SPACE -> continue
                else -> {
                    if (node != null) return null
                    node = child
                }
            }
        }
        return when (val top = node) {
            null -> null
            else -> buildBlock(text, top, leading, trailing)
        }
    }

    private fun buildBlock(content: String, node: ASTNode, leading: Int, trailing: Int): Block? {
        headingStyle(node)?.let { style ->
            // 库用 contentChildType = ATX_CONTENT 取标题正文（前导空格由 annotator 吃掉）。
            val body = node.findChildOfType(MarkdownTokenTypes.ATX_CONTENT) ?: node
            if (containsUnsupportedInline(body)) return null
            return Block(
                text = annotated(content, body, style),
                style = style,
                leadingBreaks = leading,
                trailingBreaks = trailing,
                isHeading = true
            )
        }

        if (node.type == MarkdownElementTypes.PARAGRAPH) {
            if (containsUnsupportedInline(node)) return null
            return Block(
                text = annotated(content, node, BodyStyle),
                style = BodyStyle,
                leadingBreaks = leading,
                trailingBreaks = trailing
            )
        }

        val ordered = node.type == MarkdownElementTypes.ORDERED_LIST
        if (ordered || node.type == MarkdownElementTypes.UNORDERED_LIST) {
            return buildListItem(content, node, ordered, leading, trailing)
        }
        return null
    }

    /**
     * 单个顶层列表项。
     *
     * 只接受「一个 LIST_ITEM，其内容是一个 PARAGRAPH」这种最简形态——这正是
     * [StreamingMarkdown.blocks] 按列表项下刀后的产物。嵌套子列表、任务列表（复选框）、
     * 一项里多个段落都回退给 `Markdown()`，那些结构的复刻不值当。
     */
    private fun buildListItem(
        content: String,
        node: ASTNode,
        ordered: Boolean,
        leading: Int,
        trailing: Int
    ): Block? {
        val items = node.children.filter { it.type == MarkdownElementTypes.LIST_ITEM }
        val item = items.singleOrNull() ?: return null

        var paragraph: ASTNode? = null
        for (child in item.children) {
            when (child.type) {
                MarkdownTokenTypes.LIST_BULLET,
                MarkdownTokenTypes.LIST_NUMBER,
                MarkdownTokenTypes.WHITE_SPACE,
                MarkdownTokenTypes.EOL -> continue
                MarkdownElementTypes.PARAGRAPH -> {
                    if (paragraph != null) return null
                    paragraph = child
                }
                // 复选框、嵌套列表、项内代码块等一律回退
                else -> return null
            }
        }
        val body = paragraph ?: return null
        if (containsUnsupportedInline(body)) return null

        // 复刻库的默认 BulletHandler：无序固定 "• "，有序用 CommonMark 的 start number
        // （取首个列表项的前导数字），所以 `3. xxx` 单独成块仍显示为 3。
        val marker = if (ordered) {
            val number = item.getUnescapedTextInNode(content).takeWhile(Char::isDigit).toIntOrNull() ?: 1
            "$number. "
        } else {
            "• "
        }
        return Block(
            text = annotated(content, body, BodyStyle),
            style = BodyStyle,
            leadingBreaks = leading,
            trailingBreaks = trailing,
            marker = marker,
            markerBottomPadding = !ordered
        )
    }

    private fun annotated(content: String, node: ASTNode, style: TextStyle): AnnotatedString =
        content.buildMarkdownAnnotatedString(textNode = node, style = style, annotatorSettings = settings)

    /** ATX 标题 → 对应字号；非标题返回 null。 */
    private fun headingStyle(node: ASTNode): TextStyle? = when (node.type) {
        MarkdownElementTypes.ATX_1 -> H1Style
        MarkdownElementTypes.ATX_2 -> H2Style
        MarkdownElementTypes.ATX_3 -> H3Style
        MarkdownElementTypes.ATX_4 -> H4Style
        MarkdownElementTypes.ATX_5 -> H5Style
        MarkdownElementTypes.ATX_6 -> H6Style
        else -> null
    }

    /**
     * 块里是否含**这条路径撑不住**的行内元素。
     *
     * - 图片：库用 `appendInlineContent` 占位，需要 Text 侧提供同名 `inlineContent`，
     *   缺了会直接抛异常；
     * - 链接（含裸 URL 自动链接）：库用 `withLink` 挂 `LinkAnnotation`，行为依赖
     *   `LinkInteractionListener` 与引用定义表，这里两者都没有。
     *
     * 都是「交回 `Markdown()` 就正确」的情况，所以不去补齐，直接让它回退。
     */
    private fun containsUnsupportedInline(node: ASTNode): Boolean {
        when (node.type) {
            MarkdownElementTypes.IMAGE,
            MarkdownElementTypes.AUTOLINK,
            MarkdownElementTypes.INLINE_LINK,
            MarkdownElementTypes.SHORT_REFERENCE_LINK,
            MarkdownElementTypes.FULL_REFERENCE_LINK,
            GFMTokenTypes.GFM_AUTOLINK -> return true
        }
        return node.children.any { containsUnsupportedInline(it) }
    }

    /**
     * 给末尾 [count] 个字符叠一层渐进透明，让新出的字是「浮现」而不是「蹦出」。
     *
     * 对应 FluidMarkdown 的 `OpacitySpan`（它固定取末尾 10 个字符，alpha 从 0 递增到 229）。
     *
     * 这不只是观感修饰，而是**降频的前提**：出字节拍拉长后单次推进的字数变多，
     * 没有渐显就能明显看出「一跳一跳」；有了渐显，低频出字依然像连续流动，
     * 于是低端机上可以放心把每秒绘制次数降下来（见 `Typewriter.adaptiveEmitInterval`）。
     *
     * 只跟着出字推进重算，不是每帧动画——本身不增加任何绘制次数。
     *
     * 末尾这一小段若原本带自定义颜色（如行内代码）会被这里的 alpha 颜色覆盖；本项目行内代码
     * 与正文同色，所以无差异。链接不会走到这条路径。
     */
    fun fadeTail(base: AnnotatedString, color: Color, count: Int = FADE_TAIL_CHARS): AnnotatedString =
        traced(TraceLabels.FADE) { fadeTailInternal(base, color, count) }

    private fun fadeTailInternal(base: AnnotatedString, color: Color, count: Int): AnnotatedString {
        if (count <= 0 || base.isEmpty()) return base
        val from = (base.length - count).coerceAtLeast(0)
        val span = base.length - from
        return buildAnnotatedString {
            append(base)
            for (i in from until base.length) {
                // 越靠末尾越淡：倒数第一个字符最淡，往前线性回到不透明。
                // 分母取 span + 1 而不是 span，让最后一个字符留一点可见度（FluidMarkdown 取 0，
                // 那样看起来像"少一个字"）。
                val fromEnd = base.length - i
                val alpha = fromEnd.toFloat() / (span + 1)
                addStyle(SpanStyle(color = color.copy(alpha = alpha)), i, i + 1)
            }
        }
    }

    /** 渐显覆盖的字符数。与 FluidMarkdown 的 `GRADIANT_COUNT` 取值一致。 */
    const val FADE_TAIL_CHARS = 10
}
