package com.jk.offermate.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.jeziellago.compose.markdowntext.MarkdownText as LibMarkdownText

/**
 * Markdown 渲染。委托给 [jeziellago/compose-markdown](https://github.com/jeziellago/compose-markdown)
 * （Markwon 内核，TextView 承载），支持**代码块、表格、任务列表、行内样式、链接、图片**等，
 * 且不依赖 Compose 的 `BasicText` 实现，兼容当前 Compose 版本（避免 mikepenz 需 Compose 1.8 的运行时崩溃）。
 *
 * 保留 `MarkdownText(text)` 包装以稳定调用方 API（题目卡片、追问气泡）。
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
