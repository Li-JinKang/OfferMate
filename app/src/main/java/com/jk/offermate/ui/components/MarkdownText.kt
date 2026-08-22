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
import com.mikepenz.markdown.model.markdownAnimations
import com.mikepenz.markdown.model.rememberMarkdownState

private val CodeBackground = Color(0xFFF6F7FB)

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
fun MarkdownText(markdown: String, modifier: Modifier = Modifier) {
    // immediate = true：在组合期间同步完成解析，首帧即为 Success，
    // 避免库默认的异步解析先渲染 Loading（空白）再渲染内容导致的“闪一下/渲染两次”。
    val markdownState = rememberMarkdownState(markdown, immediate = true)
    Markdown(
        markdownState = markdownState,
        modifier = modifier,
        colors = markdownColor(
            text = TextPrimary,
            linkText = Indigo,
            // 行内代码不加底色（库默认会填灰底），改为仅靠等宽小字号区分
            inlineCodeBackground = Color.Transparent
        ),
        typography = chatMarkdownTypography(),
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

/**
 * 对话专用的 Markdown 排版。库默认把 h1/h2 映射到 displaySmall/headlineMedium（36/28sp），
 * 在手机对话里标题会大到占满半屏。这里收敛到贴近主流 AI 对话的克制字号。
 */
@Composable
private fun chatMarkdownTypography() = markdownTypography(
    h1 = TextStyle(color = TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold, lineHeight = 30.sp),
    h2 = TextStyle(color = TextPrimary, fontSize = 19.sp, fontWeight = FontWeight.Bold, lineHeight = 27.sp),
    h3 = TextStyle(color = TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, lineHeight = 24.sp),
    h4 = TextStyle(color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, lineHeight = 22.sp),
    h5 = TextStyle(color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, lineHeight = 20.sp),
    h6 = TextStyle(color = TextSecondary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, lineHeight = 18.sp),
    text = TextStyle(color = TextPrimary, fontSize = 15.sp, lineHeight = 23.sp),
    paragraph = TextStyle(color = TextPrimary, fontSize = 15.sp, lineHeight = 23.sp),
    ordered = TextStyle(color = TextPrimary, fontSize = 15.sp, lineHeight = 23.sp),
    bullet = TextStyle(color = TextPrimary, fontSize = 15.sp, lineHeight = 23.sp),
    list = TextStyle(color = TextPrimary, fontSize = 15.sp, lineHeight = 23.sp),
    // quote 颜色决定引用块左侧竖条的颜色 —— 从灰色改为天蓝色
    quote = TextStyle(color = QuoteAccent, fontSize = 15.sp, lineHeight = 23.sp),
    code = TextStyle(color = TextPrimary, fontFamily = FontFamily.Monospace, fontSize = 13.sp, lineHeight = 20.sp),
    // 行内代码：无底色，靠等宽 + 更小字号（较正文 15sp 小）与正文区分
    inlineCode = TextStyle(color = TextPrimary, fontFamily = FontFamily.Monospace, fontSize = 13.sp)
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
