package com.jk.offermate.ui.components

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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.jk.offermate.ui.theme.TextSecondary
import com.mikepenz.markdown.compose.components.markdownComponents
import com.mikepenz.markdown.m3.Markdown

private val CodeBackground = Color(0xFFF3F4F6)

/**
 * Markdown 渲染。使用 [mikepenz/multiplatform-markdown-renderer](https://github.com/mikepenz/multiplatform-markdown-renderer)
 * （纯 Compose + Material3），支持标题、列表、**表格**、行内样式、链接、图片等。
 * 代码块用自定义 [CodeCard] 渲染为**带语言标签 + 复制按钮**的等宽卡片（参考主流 AI 对话的代码块样式）。
 *
 * 保留 `MarkdownText(text)` 包装以稳定调用方 API（题目卡片、追问气泡）。
 */
@Composable
fun MarkdownText(markdown: String, modifier: Modifier = Modifier) {
    Markdown(
        content = markdown,
        modifier = modifier,
        components = markdownComponents(
            codeFence = { CodeCard(it.content) },
            codeBlock = { CodeCard(it.content) }
        )
    )
}

@Composable
private fun CodeCard(raw: String) {
    val (language, code) = remember(raw) { parseCode(raw) }
    val clipboard = LocalClipboardManager.current

    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = CodeBackground)
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
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
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp)
                    .padding(bottom = 12.dp)
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
        if (body.isNotEmpty() && body.last().trim() == "```") {
            body = body.dropLast(1)
        }
        return language to body.joinToString("\n")
    }
    return "" to trimmed.trimIndent()
}
