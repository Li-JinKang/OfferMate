package com.jk.offermate.agent

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArrayBuilder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
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
) : AiClient, ToolCallingLlm {

    override suspend fun chat(messages: List<ChatMessage>): String =
        parseContent(post(buildRequestBody(modelProvider(), messages, emptyList())))

    override suspend fun chat(messages: List<ChatMessage>, tools: List<ToolSpec>): LlmTurn =
        parseTurn(post(buildRequestBody(modelProvider(), messages, tools)))

    /** 发送请求，返回原始响应文本；非 2xx 抛 [AiException]。 */
    private suspend fun post(bodyJson: String): String {
        val apiKey = apiKeyProvider().trim()
        if (apiKey.isEmpty()) throw AiException("未配置 API Key，请在设置中填写")

        val endpoint = baseUrlProvider().trim().trimEnd('/') + "/chat/completions"
        val request = Request.Builder()
            .url(endpoint)
            .header("Authorization", "Bearer $apiKey")
            .header("Content-Type", "application/json")
            .post(bodyJson.toRequestBody(JSON_MEDIA_TYPE))
            .build()

        val (code, text) = withContext(Dispatchers.IO) {
            client.newCall(request).execute().use { resp ->
                resp.code to resp.body?.string().orEmpty()
            }
        }
        if (code !in 200..299) {
            throw AiException("DeepSeek 调用失败（HTTP $code）：${text.take(300)}")
        }
        return text
    }

    internal fun buildRequestBody(model: String, messages: List<ChatMessage>, tools: List<ToolSpec>): String {
        val obj = buildJsonObject {
            put("model", model)
            put("stream", false)
            // 显式拉高输出上限：默认 4096 容易在多题长答案时被截断导致 JSON 不完整。
            put("max_tokens", MAX_OUTPUT_TOKENS)
            putJsonArray("messages") { messages.forEach { serializeMessage(it) } }
            if (tools.isNotEmpty()) {
                putJsonArray("tools") {
                    tools.forEach { spec ->
                        addJsonObject {
                            put("type", "function")
                            putJsonObject("function") {
                                put("name", spec.name)
                                put("description", spec.description)
                                put("parameters", parseSchema(spec.parametersJson))
                            }
                        }
                    }
                }
            }
        }
        return obj.toString()
    }

    private fun JsonArrayBuilder.serializeMessage(m: ChatMessage) {
        addJsonObject {
            put("role", m.role.name.lowercase())
            put("content", m.content)
            if (m.role == Role.TOOL) {
                m.toolCallId?.let { put("tool_call_id", it) }
            } else if (m.toolCalls.isNotEmpty()) {
                putJsonArray("tool_calls") {
                    m.toolCalls.forEach { c ->
                        addJsonObject {
                            put("id", c.id)
                            put("type", "function")
                            putJsonObject("function") {
                                put("name", c.name)
                                put("arguments", c.argumentsJson)
                            }
                        }
                    }
                }
            }
        }
    }

    private fun parseSchema(json: String): JsonElement =
        if (json.isBlank()) buildJsonObject { put("type", "object") }
        else runCatching { JsonSupport.json.parseToJsonElement(json) }
            .getOrDefault(buildJsonObject { put("type", "object") })

    /** 解析一轮响应：有 tool_calls 则为工具调用，否则为最终文本。 */
    internal fun parseTurn(raw: String): LlmTurn {
        val message = firstMessage(raw)
        val toolCalls = message["tool_calls"]?.jsonArray
        if (!toolCalls.isNullOrEmpty()) {
            val calls = toolCalls.mapNotNull { element ->
                val obj = element.jsonObject
                val fn = obj["function"]?.jsonObject ?: return@mapNotNull null
                ToolCall(
                    id = obj["id"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                    name = fn["name"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                    argumentsJson = fn["arguments"]?.jsonPrimitive?.contentOrNull ?: "{}"
                )
            }
            if (calls.isNotEmpty()) return LlmTurn.ToolInvocations(calls)
        }
        return LlmTurn.Final(message["content"]?.jsonPrimitive?.contentOrNull.orEmpty())
    }

    private fun firstMessage(raw: String) = run {
        val root = try {
            JsonSupport.json.parseToJsonElement(raw).jsonObject
        } catch (e: Exception) {
            throw AiException("无法解析 DeepSeek 响应：${raw.take(200)}", e)
        }
        val choices = root["choices"]?.jsonArray
            ?: throw AiException("DeepSeek 响应缺少 choices：${raw.take(200)}")
        val first = choices.firstOrNull()?.jsonObject
            ?: throw AiException("DeepSeek 响应 choices 为空")
        first["message"]?.jsonObject ?: throw AiException("DeepSeek 响应缺少 message")
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

        /** DeepSeek 支持的最大输出 token（deepseek-chat 上限 8192），显式拉满以防截断。 */
        const val MAX_OUTPUT_TOKENS = 8192

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
