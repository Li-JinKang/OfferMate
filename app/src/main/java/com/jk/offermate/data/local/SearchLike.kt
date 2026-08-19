package com.jk.offermate.data.local

/**
 * 把用户输入转成安全的 SQL LIKE 子串模式：`%关键词%`。
 *
 * 转义 `\ % _`（配合 DAO 查询里的 `ESCAPE '\'`），避免用户输入里的通配符改变匹配语义
 * （例如搜「50%」不应把 `%` 当通配）。Room 的参数绑定已防 SQL 注入，这里只处理 LIKE 通配。
 */
internal fun toLikePattern(query: String): String {
    val escaped = query
        .replace("\\", "\\\\")
        .replace("%", "\\%")
        .replace("_", "\\_")
    return "%$escaped%"
}

/**
 * 从命中文本中截取关键词附近的一段作为展示片段，控制堆占用与展示长度。
 * 找不到关键词（例如仅标题命中）时返回原文的前 [maxLen] 字符。
 */
internal fun buildSnippet(content: String, query: String, maxLen: Int = 90): String {
    val idx = content.indexOf(query, ignoreCase = true)
    if (idx < 0) {
        return if (content.length <= maxLen) content else content.take(maxLen).trimEnd() + "…"
    }
    val ctxBefore = 24
    val start = (idx - ctxBefore).coerceAtLeast(0)
    val end = (idx + query.length + (maxLen - ctxBefore)).coerceAtMost(content.length)
    val prefix = if (start > 0) "…" else ""
    val suffix = if (end < content.length) "…" else ""
    return prefix + content.substring(start, end).trim() + suffix
}
