package com.jk.offermate.data.reader

import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * 抓取 URL 的原始 HTML。失败或非 HTML 时返回 null。
 */
interface HtmlFetcher {
    fun fetch(url: String): String?
}

/**
 * 基于 OkHttp 的静态抓取实现。
 */
class OkHttpHtmlFetcher(
    private val client: OkHttpClient = OkHttpUrlResolver.defaultClient()
) : HtmlFetcher {

    override fun fetch(url: String): String? = runCatching {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", OkHttpUrlResolver.MOBILE_UA)
            .get()
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            response.body?.string()
        }
    }.getOrNull()
}
