package com.jk.offermate.data.reader

import org.jsoup.Jsoup

/**
 * 从帖子页面中提取**帖子自身的图片 URL**（面经常以长图形式发布，供后续 OCR 使用）。
 *
 * - 小红书：从 `window.__INITIAL_STATE__` 的**第一个** `imageList`（即当前笔记）取图，
 *   每张优先选 `infoList` 中 `imageScene == "H5_DTL"` 的详情大图（最清晰）。
 *   刻意只取第一个 imageList，避开"推荐笔记"的图；`<img>` 头像/静态资源天然不在其中。
 * - 通用/牛客：解析正文 HTML 的 `<img>` 标签。
 *
 * 纯逻辑、无网络，可用 HTML 夹具做 JVM 单测。
 */
object PostImageExtractor {

    /** 小红书笔记的图片（有序、去重）。 */
    fun extractXhsImageUrls(html: String): List<String> {
        if (!html.contains("__INITIAL_STATE__")) return emptyList()
        val state = html.substringAfter("__INITIAL_STATE__")

        val keyIdx = state.indexOf("\"imageList\"")
        if (keyIdx < 0) return emptyList()
        val bracketIdx = state.indexOf('[', keyIdx)
        if (bracketIdx < 0) return emptyList()
        val array = sliceBalancedArray(state, bracketIdx)

        // 取 imageList 数组内所有 url（该数组仅含当前笔记的图片），解码转义。
        // 字段顺序在不同响应下会变，故不依赖 "imageScene":"H5_DTL" 紧邻 url。
        val all = Regex("\"url\":\"((?:\\\\.|[^\"\\\\])*)\"")
            .findAll(array)
            .map { unescapeJson(it.groupValues[1]) }
            .filter(::isNoteImage)
            .toList()

        // 详情大图以 "!h5_" 结尾，预览小图以 "!style_" 结尾。优先详情大图（清晰，利于 OCR）。
        val fullRes = all.filterNot { it.contains("!style_") }.distinct()
        return fullRes.ifEmpty { all.distinct() }
    }

    /** 通用/牛客：正文 HTML 的 `<img>`（绝对化 + 去重，忽略 data URI）。 */
    fun extractHtmlImageUrls(html: String, baseUrl: String): List<String> {
        val doc = Jsoup.parse(html, baseUrl)
        return doc.select("img[src]")
            .map { it.absUrl("src") }
            .filter { it.startsWith("http", ignoreCase = true) }
            .distinct()
    }

    private fun isNoteImage(url: String): Boolean =
        url.contains("notes_pre_post") || url.contains("!h5_") || url.contains("sns-webpic")

    /** 从 `[` 起返回配平的数组文本（尊重字符串与转义，跳过嵌套 `[]`）。 */
    private fun sliceBalancedArray(s: String, openIndex: Int): String {
        var depth = 0
        var i = openIndex
        var inString = false
        var escaped = false
        while (i < s.length) {
            val c = s[i]
            if (inString) {
                when {
                    escaped -> escaped = false
                    c == '\\' -> escaped = true
                    c == '"' -> inString = false
                }
            } else {
                when (c) {
                    '"' -> inString = true
                    '[' -> depth++
                    ']' -> {
                        depth--
                        if (depth == 0) return s.substring(openIndex, i + 1)
                    }
                }
            }
            i++
        }
        return s.substring(openIndex)
    }

    /** JSON 字符串反转义（处理 `\u002F` 等，还原真实 URL）。 */
    private fun unescapeJson(s: String): String {
        if (!s.contains('\\')) return s
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
                            s.substring(i + 2, i + 6).toIntOrNull(16)?.let { sb.append(it.toChar()) }
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
