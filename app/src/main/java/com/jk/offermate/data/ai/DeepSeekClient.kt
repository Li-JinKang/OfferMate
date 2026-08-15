package com.jk.offermate.data.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * DeepSeek 的 [AiClient] 实现（OpenAI 兼容 /chat/completions）。BYOK：Key 与模型名运行时读取。
 *
 * 用 OkHttp 直连 + kotlinx-serialization 运行时 API 构造/解析（不需要 serialization 编译器插件）。
 * baseUrl 与 client 可注入，便于用 MockWebServer 做 JVM 测试。
 */
class DeepSeekClient(
    private val apiKeyProvider: suspend () -> String,
    private val modelProvider: suspend () -> String = { AiDefaults.MODEL },
    private val baseUrlProvider: suspend () -> String = { "https://api.deepseek.com/" },
    private val client: OkHttpClient = defaultClient()
) : AiClient {

    override suspend fun chat(messages: List<ChatMessage>): String {
        val apiKey = apiKeyProvider().trim()
        if (apiKey.isEmpty()) throw AiException("未配置 API Key，请在设置中填写")

        val endpoint = baseUrlProvider().trim().trimEnd('/') + "/chat/completions"
        val requestBody = buildRequestBody(modelProvider(), messages)
        val request = Request.Builder()
            .url(endpoint)
            .header("Authorization", "Bearer $apiKey")
            .header("Content-Type", "application/json")
            .post(requestBody.toRequestBody(JSON_MEDIA_TYPE))
            .build()

        val (code, text) = withContext(Dispatchers.IO) {
            client.newCall(request).execute().use { resp ->
                resp.code to resp.body?.string().orEmpty()
            }
        }
        if (code !in 200..299) {
            throw AiException("DeepSeek 调用失败（HTTP $code）：${text.take(300)}")
        }
        return parseContent(text)
    }

    private fun buildRequestBody(model: String, messages: List<ChatMessage>): String {
        val obj = buildJsonObject {
            put("model", model)
            put("stream", false)
            putJsonArray("messages") {
                messages.forEach { m ->
                    addJsonObject {
                        put("role", m.role.name.lowercase())
                        put("content", m.content)
                    }
                }
            }
        }
        return obj.toString()
    }

    private fun parseContent(raw: String): String {
        val root = try {
            JsonSupport.json.parseToJsonElement(raw).jsonObject
        } catch (e: Exception) {
            throw AiException("无法解析 DeepSeek 响应：${raw.take(200)}", e)
        }
        val choices = root["choices"]?.jsonArray
            ?: throw AiException("DeepSeek 响应缺少 choices：${raw.take(200)}")
        val first = choices.firstOrNull()?.jsonObject
            ?: throw AiException("DeepSeek 响应 choices 为空")
        return first["message"]?.jsonObject?.get("content")?.jsonPrimitive?.contentOrNull
            ?: throw AiException("DeepSeek 响应缺少 message.content")
    }

    private companion object {
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

        /** DeepSeek 生成长文本较慢，给足读超时，避免 SocketTimeout。 */
        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .callTimeout(180, TimeUnit.SECONDS)
            .build()
    }
}

/** AI 相关默认值。 */
object AiDefaults {
    const val MODEL = "deepseek-chat"
}
