package com.jk.offermate.data.reader

/**
 * WebView 动态渲染读取（用于 JS 渲染型页面）。Android 侧实现；单测用 fake。
 */
interface DynamicContentReader {
    suspend fun read(url: String): PostContent?
}

/** 链接读取结果。 */
sealed interface ReadResult {
    data class Success(val content: PostContent) : ReadResult

    /** 自动读取失败，需要用户手动粘贴正文。 */
    data class NeedsManualInput(val resolvedUrl: String, val reason: String) : ReadResult
}

/**
 * 链接读取门面，按以下顺序降级：
 *  1. 展开短链
 *  2. 静态抓取 + 正文提取
 *  3. WebView 动态渲染（若可用）
 *  4. 提示手动粘贴
 *
 * 通过接口注入网络/WebView 依赖，因此核心决策逻辑可在 JVM 单测中覆盖。
 */
class ContentReader(
    private val urlResolver: UrlResolver,
    private val htmlFetcher: HtmlFetcher,
    private val extractor: HtmlContentExtractor,
    private val dynamicReader: DynamicContentReader? = null
) {

    suspend fun read(rawUrl: String): ReadResult {
        val resolved = urlResolver.resolve(rawUrl)
        val notExpanded = looksLikeShortLink(resolved)

        // 1) 静态抓取
        val html = htmlFetcher.fetch(resolved)
        if (html != null) {
            val content = extractor.extract(html, resolved)
            if (content.isUsable) return ReadResult.Success(content)
        }

        // 2) WebView 动态渲染兜底
        val dynamic = runCatching { dynamicReader?.read(resolved) }.getOrNull()
        if (dynamic != null && dynamic.isUsable) return ReadResult.Success(dynamic)

        // 3) 手动粘贴兜底 —— 按卡点给出可诊断的原因
        val reason = when {
            notExpanded -> "短链未能展开为真实地址（网络异常或被平台风控），请手动粘贴正文"
            html == null -> "页面抓取失败（网络异常，或被风控/需登录），请手动粘贴正文"
            else -> "已获取页面但未提取到正文（可能是图片面经或需登录态），请手动粘贴正文"
        }
        return ReadResult.NeedsManualInput(resolvedUrl = resolved, reason = reason)
    }

    /** 展开后仍像短链/跳转链，说明重定向未被成功跟随。 */
    private fun looksLikeShortLink(url: String): Boolean =
        SHORT_LINK_MARKERS.any { url.contains(it, ignoreCase = true) }

    private companion object {
        val SHORT_LINK_MARKERS = listOf("xhslink", "/share/jump", "b23.tv", "t.cn")
    }
}
