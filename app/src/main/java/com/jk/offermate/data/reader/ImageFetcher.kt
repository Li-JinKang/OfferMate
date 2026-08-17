package com.jk.offermate.data.reader

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/** 下载图片字节（供 OCR）。抽象成接口便于测试注入。 */
interface ImageFetcher {
    suspend fun fetch(url: String): ByteArray?
}

class OkHttpImageFetcher(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()
) : ImageFetcher {

    override suspend fun fetch(url: String): ByteArray? = withContext(Dispatchers.IO) {
        // 图源多为 http 明文，设备默认禁明文：优先尝试 https，失败回退原始 url
        download(toHttps(url)) ?: download(url)
    }

    private fun download(url: String): ByteArray? = runCatching {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", UA)
            .header("Referer", "https://www.xiaohongshu.com/")
            .get()
            .build()
        client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) null else resp.body?.bytes()
        }
    }.getOrNull()

    private fun toHttps(url: String): String =
        if (url.startsWith("http://")) "https://" + url.removePrefix("http://") else url

    private companion object {
        const val UA =
            "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
    }
}
