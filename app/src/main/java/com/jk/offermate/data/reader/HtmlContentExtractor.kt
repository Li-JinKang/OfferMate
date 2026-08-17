package com.jk.offermate.data.reader

import net.dankito.readability4j.Readability4J
import org.jsoup.Jsoup

/**
 * 从 HTML 字符串中提取正文纯文本。
 *
 * 策略：优先用 Readability4J（Mozilla Readability 的 JVM 移植）抽取主要文章内容；
 * 若结果过短（如非文章型页面），退化为 Jsoup 的 body 文本。
 *
 * 该类是纯逻辑、无网络、无 Android 依赖，可在 JVM 单元测试中直接覆盖。
 */
class HtmlContentExtractor {

    fun extract(html: String, url: String): PostContent {
        val images = extractImages(html, url)

        // 小红书：优先从 __INITIAL_STATE__ 提取笔记正文，剔除评论/推荐噪声
        if (isXiaohongshu(url)) {
            XhsNoteExtractor.extract(html, url)?.let { return it.copy(imageUrls = images) }
        }

        val readable = runCatching {
            val article = Readability4J(url, html).parse()
            val title = article.title?.trim().orEmpty()
            val text = normalize(article.textContent.orEmpty())
            title to text
        }.getOrNull()

        if (readable != null && readable.second.length >= PostContent.MIN_USABLE_LENGTH) {
            return PostContent(
                title = readable.first,
                text = readable.second,
                sourceUrl = url,
                method = ExtractionMethod.STATIC_HTML,
                imageUrls = images
            )
        }

        // 退化路径：直接取 body 文本
        val doc = Jsoup.parse(html, url)
        val bodyText = normalize(doc.body()?.text().orEmpty())
        val title = (readable?.first?.takeIf { it.isNotEmpty() } ?: doc.title().trim())
        return PostContent(
            title = title,
            text = bodyText,
            sourceUrl = url,
            method = ExtractionMethod.STATIC_HTML,
            imageUrls = images
        )
    }

    private fun extractImages(html: String, url: String): List<String> =
        if (isXiaohongshu(url)) PostImageExtractor.extractXhsImageUrls(html)
        else PostImageExtractor.extractHtmlImageUrls(html, url)

    private fun isXiaohongshu(url: String): Boolean =
        url.contains("xiaohongshu", ignoreCase = true) ||
            url.contains("xhslink", ignoreCase = true) ||
            url.contains("xhs", ignoreCase = true)

    private fun normalize(raw: String): String =
        raw.replace('\u00A0', ' ')
            .lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .joinToString("\n")
            .trim()
}
