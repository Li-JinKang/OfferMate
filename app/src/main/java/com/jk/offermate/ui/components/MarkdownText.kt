package com.jk.offermate.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jk.offermate.ui.theme.Indigo
import com.jk.offermate.ui.theme.OutlineSoft
import com.jk.offermate.ui.theme.TextPrimary
import com.jk.offermate.ui.theme.TextSecondary
import com.mikepenz.markdown.compose.components.markdownComponents
import com.mikepenz.markdown.compose.elements.MarkdownTable
import com.mikepenz.markdown.compose.elements.MarkdownTableHeader
import com.mikepenz.markdown.compose.elements.MarkdownTableRow
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.m3.markdownColor
import com.mikepenz.markdown.m3.markdownTypography
import com.mikepenz.markdown.model.State
import com.mikepenz.markdown.model.markdownAnimations

private val CodeBackground = Color(0xFFF6F7FB)

/** [MarkdownText] 的解析策略。 */
enum class MarkdownParseMode {
    /**
     * 同步解析并缓存。首帧即有内容、不闪空白，适合静态内容（题目卡片、已落库的历史消息）。
     * 命中缓存时零成本；未命中才在当前线程解析一次。
     */
    BLOCKING,

    /**
     * 异步解析并缓存。适合流式中**已稳定的块级前缀**：它会被后续很多帧复用，值得进缓存，
     * 但不能在主线程解析。
     */
    ASYNC_CACHED,

    /**
     * 异步解析且**不进缓存**。适合流式中不断变化的尾巴——每帧都是新内容，
     * 入缓存只会把真实消息挤出 LRU。
     */
    ASYNC_TRANSIENT
}

/** 引用块（blockquote）左侧竖条颜色：天蓝色（库以 quote 文字样式的颜色绘制竖条）。 */
private val QuoteAccent = Color(0xFF38A2F0)

/**
 * Markdown 渲染。使用 [mikepenz/multiplatform-markdown-renderer](https://github.com/mikepenz/multiplatform-markdown-renderer)
 * （纯 Compose + Material3，需 Compose 1.8+），散文/标题/列表/**表格**/行内样式由其原生渲染；
 * 代码块通过 `markdownComponents(codeFence/codeBlock)` 交给自定义 [CodeCard]：
 * **语言标签 + 复制按钮 + 等宽横向滚动 + 圆角边框**，贴近主流 AI 对话的代码块样式。
 *
 * 保留 `MarkdownText(text)` 包装以稳定调用方 API（题目卡片、追问气泡）。
 */
@Composable
fun MarkdownText(
    markdown: String,
    modifier: Modifier = Modifier,
    mode: MarkdownParseMode = MarkdownParseMode.BLOCKING
) {
    // 解析走 [MarkdownStateCache]，不再用 rememberMarkdownState(immediate = true)：后者会在
    // **每次重组**都同步全量重解析（parseBlocking 写在组合体里、不在 remember 内），
    // 流式期间等于每个 token 卡一次主线程。详见 MarkdownStateCache 的注释。
    val cached = remember(markdown, mode) {
        if (mode == MarkdownParseMode.BLOCKING) {
            MarkdownStateCache.getOrParseBlocking(markdown) as? State.Success
        } else {
            MarkdownStateCache.peek(markdown)
        }
    }
    // 不能用 produceState：它内部的 `remember { mutableStateOf(initialValue) }` **没有 key**，
    // initialValue 只在首次组合生效。内容变化时它会保留上一份解析结果，而生产者又因
    // `cached != null` 直接 return，于是永远停在旧内容上——表现为“输出完成后一部分内容不显示，
    // 重进页面才正常”。这里用带 key 的 remember 显式持有异步结果。
    val asyncParsed = remember(markdown, mode) { mutableStateOf<State.Success?>(null) }
    LaunchedEffect(markdown, mode) {
        if (cached != null) return@LaunchedEffect
        asyncParsed.value = MarkdownStateCache.warm(
            content = markdown,
            store = mode != MarkdownParseMode.ASYNC_TRANSIENT
        ) as? State.Success
    }
    val parsed = cached ?: asyncParsed.value

    // 保留上一次成功的解析结果：内容刚变化、新的解析还没回来的那几帧继续渲染旧内容，
    // 而不是回落到 State.Loading（空白 Box）造成闪白。用普通字段而非 snapshot state，
    // 避免在组合期间写 state 触发额外重组。
    val holder = remember { LastSuccessHolder() }
    val current = parsed ?: holder.value
    if (parsed != null) holder.value = parsed

    Markdown(
        state = current ?: State.Loading(),
        modifier = modifier,
        colors = markdownColor(
            text = TextPrimary,
            linkText = Indigo,
            // 行内代码不加底色（库默认会填灰底），改为仅靠等宽小字号区分
            inlineCodeBackground = Color.Transparent
        ),
        typography = rememberChatMarkdownTypography(),
        // 关闭默认的 animateContentSize，内容更新（如流式）时不做尺寸动画，避免抖动。
        animations = markdownAnimations(animateTextSize = { this }),
        components = markdownComponents(
            // 注意：it.content 是整篇 Markdown 全文，需用节点偏移切出当前代码块文本
            codeFence = { CodeCard(nodeText(it.content, it.node)) },
            codeBlock = { CodeCard(nodeText(it.content, it.node)) },
            // 表格：库默认单元格 maxLines=1 + 省略号会截断内容。改为**允许换行**（不省略），
            // 单元格文字完整显示；表格总宽超出可视区时库自带横向滚动生效。
            table = {
                MarkdownTable(
                    content = it.content,
                    node = it.node,
                    style = it.typography.table,
                    headerBlock = { c, h, w, s ->
                        MarkdownTableHeader(
                            content = c, header = h, tableWidth = w, style = s,
                            maxLines = Int.MAX_VALUE, overflow = TextOverflow.Clip
                        )
                    },
                    rowBlock = { c, h, w, s ->
                        MarkdownTableRow(
                            content = c, header = h, tableWidth = w, style = s,
                            maxLines = Int.MAX_VALUE, overflow = TextOverflow.Clip
                        )
                    }
                )
            }
        )
    )
}

/** 承载上一次成功解析结果的普通持有者（非 snapshot state，写入不触发重组）。 */
private class LastSuccessHolder {
    var value: State.Success? = null
}

// 对话正文/标题的字号方案。提到顶层常量：这些 TextStyle 不依赖组合环境，
// 流式期间每帧给每个气泡重新 new 一整套纯属浪费。
private val BodyStyle = TextStyle(color = TextPrimary, fontSize = 15.sp, lineHeight = 23.sp)
private val H1Style = TextStyle(color = TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold, lineHeight = 30.sp)
private val H2Style = TextStyle(color = TextPrimary, fontSize = 19.sp, fontWeight = FontWeight.Bold, lineHeight = 27.sp)
private val H3Style = TextStyle(color = TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, lineHeight = 24.sp)
private val H4Style = TextStyle(color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, lineHeight = 22.sp)
private val H5Style = TextStyle(color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, lineHeight = 20.sp)
private val H6Style = TextStyle(color = TextSecondary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, lineHeight = 18.sp)

/** quote 颜色决定引用块左侧竖条的颜色 —— 从灰色改为天蓝色。 */
private val QuoteStyle = TextStyle(color = QuoteAccent, fontSize = 15.sp, lineHeight = 23.sp)
private val CodeStyle =
    TextStyle(color = TextPrimary, fontFamily = FontFamily.Monospace, fontSize = 13.sp, lineHeight = 20.sp)

/** 行内代码：无底色，靠等宽 + 更小字号（较正文 15sp 小）与正文区分。 */
private val InlineCodeStyle = TextStyle(color = TextPrimary, fontFamily = FontFamily.Monospace, fontSize = 13.sp)

/**
 * 对话专用的 Markdown 排版。库默认把 h1/h2 映射到 displaySmall/headlineMedium（36/28sp），
 * 在手机对话里标题会大到占满半屏。这里收敛到贴近主流 AI 对话的克制字号。
 *
 * （`markdownTypography` 本身是 @Composable，没法塞进 remember；把 TextStyle 提成顶层常量后，
 * 每次重组只剩一个轻量包装对象的分配。）
 */
@Composable
private fun rememberChatMarkdownTypography() = markdownTypography(
    h1 = H1Style,
    h2 = H2Style,
    h3 = H3Style,
    h4 = H4Style,
    h5 = H5Style,
    h6 = H6Style,
    text = BodyStyle,
    paragraph = BodyStyle,
    ordered = BodyStyle,
    bullet = BodyStyle,
    list = BodyStyle,
    quote = QuoteStyle,
    code = CodeStyle,
    inlineCode = InlineCodeStyle
)

/** 用 AST 节点的偏移量从全文中切出该节点对应的原文（含 ``` 围栏或缩进）。 */
private fun nodeText(content: String, node: org.intellij.markdown.ast.ASTNode): String {
    val start = node.startOffset.coerceIn(0, content.length)
    val end = node.endOffset.coerceIn(start, content.length)
    return content.substring(start, end)
}

@Composable
private fun CodeCard(raw: String) {
    val (language, code) = remember(raw) { parseCode(raw) }
    val clipboard = LocalClipboardManager.current

    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = CodeBackground)
    ) {
        Column(Modifier.fillMaxWidth().border(1.dp, OutlineSoft, RoundedCornerShape(12.dp))) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 14.dp, end = 6.dp, top = 2.dp, bottom = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = language.ifBlank { "code" },
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = { clipboard.setText(AnnotatedString(code)) }) {
                    Text("复制", style = MaterialTheme.typography.labelSmall)
                }
            }
            HorizontalDivider(color = OutlineSoft, thickness = 1.dp)
            Box(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 14.dp, vertical = 12.dp)
            ) {
                Text(
                    text = code,
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

/** 从节点原文解析出语言与代码正文（去掉 ``` 围栏与语言行；缩进代码块则去公共缩进）。 */
private fun parseCode(raw: String): Pair<String, String> {
    val trimmed = raw.trim()
    if (trimmed.startsWith("```")) {
        val lines = trimmed.lines()
        val language = lines.first().removePrefix("```").trim()
        var body = lines.drop(1)
        if (body.isNotEmpty() && body.last().trim() == "```") body = body.dropLast(1)
        return language to body.joinToString("\n")
    }
    return "" to trimmed.trimIndent()
}
