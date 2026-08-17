package com.jk.offermate.data.reader

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.OkHttpClient
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.junit.Test
import java.util.Base64
import java.util.concurrent.TimeUnit

/**
 * 图片面经 OCR 探针（手动运行，不做断言，只打印结果供人工校验）。
 *
 * 作用：真实抓取帖子 → 提取帖子所有图片 URL（[PostImageExtractor]）→ 打印；
 * 若提供了多模态大模型 Key，则对每张图做 OCR 并打印识别文字。
 *
 * 运行（仅打印图片 URL，无需 Key）：
 *   ./gradlew :app:testDebugUnitTest --tests "com.jk.offermate.data.reader.ImageOcrProbeTest" --console=plain
 *
 * 运行 + OCR（用你自己的视觉模型 Key，OpenAI 兼容 /chat/completions）：
 *   ./gradlew :app:testDebugUnitTest --tests "com.jk.offermate.data.reader.ImageOcrProbeTest" --console=plain \
 *     -Docr.key=sk-xxx \
 *     -Docr.model=qwen-vl-max \
 *     -Docr.baseUrl=https://dashscope.aliyuncs.com/compatible-mode/v1/
 *
 * 说明：DeepSeek 无视觉能力；请用**多模态**模型（如阿里通义 qwen-vl-max / 智谱 glm-4v）。
 */
class ImageOcrProbeTest {

    private val client = OkHttpClient.Builder()
        .followRedirects(true)
        .followSslRedirects(true)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .build()

    private val urls = listOf(
        "https://xhslink.cn/o/6Gz0nDGZxAE"
    )

    @Test
    fun probeImagesAndOcr() {
        val ocrKey = System.getProperty("ocr.key")?.takeIf { it.isNotBlank() }
        val ocrModel = System.getProperty("ocr.model") ?: "qwen-vl-max"
        val ocrBaseUrl = System.getProperty("ocr.baseUrl")
            ?: "https://dashscope.aliyuncs.com/compatible-mode/v1/"

        for (pageUrl in urls) {
            println("\n==================== 帖子: $pageUrl ====================")
            val fetched = fetch(pageUrl)
            if (fetched == null) {
                println("[抓取失败] 无法获取页面 HTML")
                continue
            }
            val (finalUrl, html) = fetched
            println("展开后URL = $finalUrl")

            val images = if (isXhs(finalUrl)) {
                PostImageExtractor.extractXhsImageUrls(html)
            } else {
                PostImageExtractor.extractHtmlImageUrls(html, finalUrl)
            }
            println("提取到图片 ${images.size} 张：")
            images.forEachIndexed { i, u -> println("  [$i] $u") }

            if (ocrKey == null) {
                println("\n(未提供 -Docr.key，跳过 OCR。补充视觉模型 Key 后可打印图片文字)")
                continue
            }

            println("\n---- OCR（模型=$ocrModel）----")
            images.forEachIndexed { i, u ->
                println("\n===== 图片[$i] OCR =====")
                val bytes = download(u)
                if (bytes == null) {
                    println("[下载失败] $u")
                    return@forEachIndexed
                }
                val text = runCatching { ocr(bytes, ocrKey, ocrModel, ocrBaseUrl) }
                    .getOrElse { "[OCR 异常] ${it.javaClass.simpleName}: ${it.message}" }
                println(text)
            }
            println("==================== END ====================\n")
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

    private fun ocr(image: ByteArray, key: String, model: String, baseUrl: String): String {
        val dataUri = "data:image/jpeg;base64," + Base64.getEncoder().encodeToString(image)
        val payload = buildJsonObject {
            put("model", model)
            putJsonArray("messages") {
                addJsonObject {
                    put("role", "user")
                    putJsonArray("content") {
                        addJsonObject {
                            put("type", "text")
                            put("text", "请识别这张图片中的所有文字，按阅读顺序原样输出，不要解释、不要总结。")
                        }
                        addJsonObject {
                            put("type", "image_url")
                            putJsonObject("image_url") { put("url", dataUri) }
                        }
                    }
                }
            }
        }
        val endpoint = baseUrl.trimEnd('/') + "/chat/completions"
        val req = Request.Builder()
            .url(endpoint)
            .header("Authorization", "Bearer $key")
            .post(payload.toString().toRequestBody("application/json".toMediaType()))
            .build()
        client.newCall(req).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) return "[HTTP ${resp.code}] $body"
            return parseContent(body)
        }
    }

    private fun parseContent(body: String): String = runCatching {
        Json { ignoreUnknownKeys = true }
            .parseToJsonElement(body)
            .jsonObject["choices"]!!.jsonArray[0]
            .jsonObject["message"]!!.jsonObject["content"]!!.jsonPrimitive.content
    }.getOrElse { "[解析失败] $body" }

    private fun isXhs(url: String): Boolean =
        url.contains("xiaohongshu", true) || url.contains("xhslink", true) || url.contains("xhs", true)

    private companion object {
        const val UA =
            "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
    }
}
