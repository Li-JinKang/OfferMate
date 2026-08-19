package com.jk.offermate.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle

/**
 * 把 [text] 中所有命中 [query] 的子串（大小写不敏感）标为高亮色 + 加粗，返回 [AnnotatedString]。
 * 用于搜索结果里的关键词高亮。[query] 为空时原样返回。
 */
fun highlightMatches(text: String, query: String, color: Color): AnnotatedString {
    val q = query.trim()
    if (q.isEmpty()) return AnnotatedString(text)
    return buildAnnotatedString {
        var i = 0
        while (i < text.length) {
            val hit = text.indexOf(q, startIndex = i, ignoreCase = true)
            if (hit < 0) {
                append(text.substring(i))
                break
            }
            append(text.substring(i, hit))
            withStyle(SpanStyle(color = color, fontWeight = FontWeight.SemiBold)) {
                append(text.substring(hit, hit + q.length))
            }
            i = hit + q.length
        }
    }
}
