package com.jk.offermate.data.reader

import android.util.Log
import com.jk.offermate.data.net.HttpClients
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/** 下载图片字节（供 OCR）。抽象成接口便于测试注入。 */
interface ImageFetcher {
    suspend fun fetch(url: String): ByteArray?
}

class OkHttpImageFetcher(
    private val client: OkHttpClient = HttpClients.image
) : ImageFetcher {

    override suspend fun fetch(url: String): ByteArray? = withContext(Dispatchers.IO) {
        // 图源多为 http 明文，设备默认禁明文：优先尝试 https，失败回退原始 url
        download(toHttps(url)) ?: download(url)
    }

    /**
     * 下载失败返回 null，但**必须留下日志**：图片面经 OCR 不出内容时，
     * 需要能区分"图片没下下来"和"下下来了但识别不出字"。
     */
    private fun download(url: String): ByteArray? = try {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", UA)
            .header("Referer", "https://www.xiaohongshu.com/")
            .get()
            .build()
        client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) {
                Log.w(TAG, "图片下载失败 HTTP ${resp.code} url=$url")
                null
            } else {
                resp.body?.bytes()
            }
        }
    } catch (e: Exception) {
        Log.w(TAG, "图片下载异常 url=$url err=${e.javaClass.simpleName}:${e.message}")
        null
    }

    private fun toHttps(url: String): String =
        if (url.startsWith("http://")) "https://" + url.removePrefix("http://") else url

    private companion object {
        const val TAG = "OfferMateOCR"
        const val UA =
            "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
    }
}
