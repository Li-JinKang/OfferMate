package com.jk.offermate.data.reader

/**
 * 从链接读取到的帖子正文（面经内容）。
 *
 * @param title    帖子标题（可能为空）
 * @param text     提取出的正文纯文本
 * @param sourceUrl 实际读取的 URL（短链已展开）
 * @param method   提取方式
 */
data class PostContent(
    val title: String,
    val text: String,
    val sourceUrl: String,
    val method: ExtractionMethod
) {
    /** 正文长度达到可用阈值才认为读取成功。 */
    val isUsable: Boolean
        get() = text.trim().length >= MIN_USABLE_LENGTH

    companion object {
        /** 少于该字符数的正文视为读取失败（如被 JS 渲染墙拦截、空壳页面）。 */
        const val MIN_USABLE_LENGTH = 40
    }
}

enum class ExtractionMethod {
    /** 静态 HTML 抓取 + 正文提取（Readability/Jsoup）。 */
    STATIC_HTML,

    /** WebView 动态渲染后提取（用于 JS 渲染型页面，如小红书）。 */
    WEBVIEW,

    /** 用户手动粘贴正文。 */
    MANUAL
}
