package com.jk.offermate.data.reader

/**
 * 小红书笔记正文提取：从页面 `window.__INITIAL_STATE__` 中取笔记的 title + desc，
 * 只保留正文，剔除评论/推荐等噪声。纯函数，可 JVM 单测。
 */
object XhsNoteExtractor {

    fun extract(html: String, url: String): PostContent? {
        if (!html.contains("__INITIAL_STATE__")) return null
        // 只在 __INITIAL_STATE__ 之后查找，笔记详情通常位于状态对象前部，取第一个 title/desc 即为笔记本体
        val state = html.substringAfter("__INITIAL_STATE__")

        val title = firstJsonString(state, "title")?.trim().orEmpty()
        val desc = firstJsonString(state, "desc")?.let(::cleanTopics)?.trim().orEmpty()

        if (title.isBlank() && desc.isBlank()) return null

        val text = listOf(title, desc).filter { it.isNotBlank() }.joinToString("\n\n")
        if (text.length < PostContent.MIN_USABLE_LENGTH) return null

        return PostContent(
            title = title,
            text = text,
            sourceUrl = url,
            method = ExtractionMethod.STATIC_HTML
        )
    }

    /** 提取 JSON 中某 key 的第一个字符串值（含转义处理）。 */
    private fun firstJsonString(source: String, key: String): String? {
        val pattern = "\"" + key + "\":\"((?:\\\\.|[^\"\\\\])*)\""
        val raw = Regex(pattern).find(source)?.groupValues?.getOrNull(1) ?: return null
        return unescapeJson(raw)
    }

    /** 去掉小红书话题占位标记 [话题]。 */
    private fun cleanTopics(desc: String): String =
        desc.replace("[话题]", "").replace(Regex("[ \\t]+"), " ")

    private fun unescapeJson(s: String): String {
        val sb = StringBuilder(s.length)
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == '\\' && i + 1 < s.length) {
                when (val n = s[i + 1]) {
                    'n' -> sb.append('\n')
                    'r' -> sb.append('\r')
                    't' -> sb.append('\t')
                    '"' -> sb.append('"')
                    '\\' -> sb.append('\\')
                    '/' -> sb.append('/')
                    'u' -> {
                        if (i + 5 < s.length) {
                            val hex = s.substring(i + 2, i + 6)
                            hex.toIntOrNull(16)?.let { sb.append(it.toChar()) }
                            i += 4
                        }
                    }
                    else -> sb.append(n)
                }
                i += 2
            } else {
                sb.append(c)
                i++
            }
        }
        return sb.toString()
    }
}
