package com.jk.offermate.data.ocr

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jk.offermate.data.reader.PostImageExtractor
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit

/**
 * 端侧 OCR 校验（在**真机/模拟器**上运行，不做断言，打印识别文字供人工校验）。
 *
 * 流程：真实抓取帖子 → [PostImageExtractor] 提取全部图片 URL → 下载 → [MlKitTextRecognizer] OCR → 打印。
 *
 * 运行：
 *   ./gradlew :app:connectedDebugAndroidTest \
 *     -Pandroid.testInstrumentationRunnerArguments.class=com.jk.offermate.data.ocr.ImageOcrInstrumentedTest
 * 或在 Android Studio 里右键运行；识别文字见 Logcat（TAG=OfferMateOCR）与测试输出。
 */
@RunWith(AndroidJUnit4::class)
class ImageOcrInstrumentedTest {

    private val client = OkHttpClient.Builder()
        .followRedirects(true)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private val pageUrl = "https://xhslink.cn/o/6Gz0nDGZxAE"

    @Test
    fun ocrPostImages() = runBlocking {
        val (finalUrl, html) = fetch(pageUrl) ?: run {
            log("抓取页面失败: $pageUrl")
            return@runBlocking
        }
        log("展开后URL = $finalUrl")

        val images = PostImageExtractor.extractXhsImageUrls(html)
        log("提取到图片 ${images.size} 张")

        val recognizer = MlKitTextRecognizer()
        try {
            images.forEachIndexed { i, url ->
                log("\n===== 图片[$i] $url =====")
                // http 明文图源在设备上可能被拦截，优先尝试 https
                val bytes = download(toHttps(url)) ?: download(url)
                if (bytes == null) {
                    log("[下载失败] $url")
                    return@forEachIndexed
                }
                val text = runCatching { recognizer.recognize(bytes) }
                    .getOrElse { "[OCR异常] ${it.javaClass.simpleName}: ${it.message}" }
                log("---- OCR[$i] 结果 ----\n$text")
            }
        } finally {
            recognizer.close()
        }
    }

    private fun fetch(url: String): Pair<String, String>? = runCatching {
        val req = Request.Builder().url(url).header("User-Agent", UA).get().build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return null
            val body = resp.body?.string() ?: return null
            resp.request.url.toString() to body
        }
    }.getOrNull()

    private fun download(url: String): ByteArray? = runCatching {
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", UA)
            .header("Referer", "https://www.xiaohongshu.com/")
            .get()
            .build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return null
            resp.body?.bytes()
        }
    }.getOrNull()

    private fun toHttps(url: String): String =
        if (url.startsWith("http://")) "https://" + url.removePrefix("http://") else url

    private fun log(msg: String) {
        println(msg)
        Log.i(TAG, msg)
    }

    private companion object {
        const val TAG = "OfferMateOCR"
        const val UA =
            "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
    }
}
