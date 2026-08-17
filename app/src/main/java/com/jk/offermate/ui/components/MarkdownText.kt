package com.jk.offermate.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.jeziellago.compose.markdowntext.MarkdownText as LibMarkdownText

/**
 * Markdown 渲染。内部委托给 [jeziellago/compose-markdown](https://github.com/jeziellago/compose-markdown)
 * （Markwon 内核），支持**代码块、表格、任务列表、行内样式、链接、图片**等，取代此前的简易自研实现。
 *
 * 保留此包装以稳定调用方 API（题目卡片、追问气泡等仍调用 `MarkdownText(text)`），并统一注入主题样式。
 */
@Composable
fun MarkdownText(markdown: String, modifier: Modifier = Modifier) {
    LibMarkdownText(
        markdown = markdown,
        modifier = modifier,
        style = MaterialTheme.typography.bodyMedium.copy(
            color = MaterialTheme.colorScheme.onSurface
        )
    )
}
