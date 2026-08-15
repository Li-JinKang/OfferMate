package com.jk.offermate.data.reader

import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * 展开分享短链（如 xhslink.cn）到真实 URL。
 */
interface UrlResolver {
    /** 返回跟随重定向后的最终 URL；失败时返回原始 URL。 */
    fun resolve(url: String): String
}

/**
 * 基于 OkHttp 的实现：跟随重定向，取最终请求的 URL。
 */
class OkHttpUrlResolver(
    private val client: OkHttpClient = defaultClient()
) : UrlResolver {

    override fun resolve(url: String): String = runCatching {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", MOBILE_UA)
            .get()
            .build()
        client.newCall(request).execute().use { response ->
            response.request.url.toString()
        }
    }.getOrDefault(url)

    companion object {
        const val MOBILE_UA =
            "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .followRedirects(true)
            .followSslRedirects(true)
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }
}
