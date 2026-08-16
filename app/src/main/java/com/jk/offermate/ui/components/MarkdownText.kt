package com.jk.offermate.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * 轻量 Markdown 渲染：支持标题、无序列表(减号或星号)、有序列表(数字加点)、行内加粗与代码。
 * 面向 AI 分点答案，非完整 Markdown 实现。
 */
@Composable
fun MarkdownText(markdown: String, modifier: Modifier = Modifier) {
    val lines = markdown.trim().replace("\r\n", "\n").split("\n")
    Column(modifier) {
        lines.forEach { rawLine ->
            val line = rawLine.trimEnd()
            val trimmed = line.trimStart()
            when {
                trimmed.isBlank() -> Spacer(Modifier.height(6.dp))

                trimmed.startsWith("### ") ->
                    Text(parseInline(trimmed.removePrefix("### ")), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)

                trimmed.startsWith("## ") ->
                    Text(parseInline(trimmed.removePrefix("## ")), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)

                trimmed.startsWith("# ") ->
                    Text(parseInline(trimmed.removePrefix("# ")), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

                trimmed.startsWith("- ") || trimmed.startsWith("* ") ->
                    BulletRow("•", parseInline(trimmed.drop(2)))

                ORDERED.matchAt(trimmed, 0) != null -> {
                    val m = ORDERED.find(trimmed)!!
                    BulletRow(m.value.trim(), parseInline(trimmed.removePrefix(m.value)))
                }

                else ->
                    Text(parseInline(trimmed), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun BulletRow(marker: String, content: AnnotatedString) {
    Row {
        Text("$marker ", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
        Text(content, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
    }
}

private val ORDERED = Regex("^\\d+\\.\\s")

/** 解析行内 **加粗** 与 `代码`。 */
private fun parseInline(text: String): AnnotatedString = buildAnnotatedString {
    var i = 0
    var bold = false
    var code = false
    while (i < text.length) {
        if (i + 1 < text.length && text[i] == '*' && text[i + 1] == '*') {
            if (bold) pop() else pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
            bold = !bold
            i += 2
            continue
        }
        if (text[i] == '`') {
            if (code) pop() else pushStyle(SpanStyle(fontFamily = FontFamily.Monospace))
            code = !code
            i += 1
            continue
        }
        append(text[i])
        i += 1
    }
    if (bold) pop()
    if (code) pop()
}
