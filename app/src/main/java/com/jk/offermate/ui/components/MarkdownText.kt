package com.jk.offermate.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.jk.offermate.ui.theme.OutlineSoft
import com.jk.offermate.ui.theme.TextSecondary
import dev.jeziellago.compose.markdowntext.MarkdownText as LibMarkdownText

private val CodeBackground = Color(0xFFF6F7FB)

/**
 * Markdown 渲染。按 ``` 代码围栏**分段**：
 * - 代码块 → 自定义 [CodeCard]（语言标签 + 复制按钮 + 等宽横向滚动，参考主流 AI 对话样式）；
 * - 其余散文 → [jeziellago/compose-markdown](https://github.com/jeziellago/compose-markdown)（Markwon 内核，
 *   兼容当前 Compose 版本，支持标题/列表/表格/行内样式）。
 *
 * 这样既得到美观的代码卡，又不引入需要 Compose 1.8 的纯 Compose 库（避免运行时崩溃）。
 */
@Composable
fun MarkdownText(markdown: String, modifier: Modifier = Modifier) {
    val blocks = remember(markdown) { splitIntoBlocks(markdown) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        blocks.forEach { block ->
            when (block) {
                is MdBlock.Code -> CodeCard(block.language, block.code)
                is MdBlock.Prose -> if (block.text.isNotBlank()) {
                    LibMarkdownText(
                        markdown = block.text.trim(),
                        modifier = Modifier.fillMaxWidth(),
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun CodeCard(language: String, code: String) {
    val clipboard = LocalClipboardManager.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = CodeBackground)
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .border(1.dp, OutlineSoft, RoundedCornerShape(12.dp))
        ) {
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

private sealed interface MdBlock {
    data class Prose(val text: String) : MdBlock
    data class Code(val language: String, val code: String) : MdBlock
}

private val FENCE = Regex("```([^\\n`]*)\\n([\\s\\S]*?)```")

/** 按 ``` 围栏切分为散文/代码块（去掉行内 ``` 干扰交给正则的非贪婪匹配）。 */
private fun splitIntoBlocks(markdown: String): List<MdBlock> {
    val text = markdown.replace("\r\n", "\n")
    val blocks = mutableListOf<MdBlock>()
    var cursor = 0
    for (match in FENCE.findAll(text)) {
        if (match.range.first > cursor) {
            blocks += MdBlock.Prose(text.substring(cursor, match.range.first))
        }
        val language = match.groupValues[1].trim()
        val code = match.groupValues[2].trimEnd('\n')
        blocks += MdBlock.Code(language, code)
        cursor = match.range.last + 1
    }
    if (cursor < text.length) {
        blocks += MdBlock.Prose(text.substring(cursor))
    }
    if (blocks.isEmpty()) blocks += MdBlock.Prose(text)
    return blocks
}
