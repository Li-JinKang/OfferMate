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
    private val client: OkHttpClient = defaultClient(),
    private val logger: AgentLogger = NoopAgentLogger
) : AiClient, ToolCallingLlm, StreamingLlm {

    override suspend fun chat(messages: List<ChatMessage>): String {
        val model = modelProvider()
        logger.d { "AI 请求(chat)：model=$model，消息=${messages.size}" }
        val content = parseContent(post(buildRequestBody(model, messages, emptyList())))
        logger.d { "AI 响应(chat)：文本 len=${content.length}" }
        return content
    }

    override suspend fun chat(messages: List<ChatMessage>, tools: List<ToolSpec>): LlmTurn {
        val model = modelProvider()
        logger.d { "AI 请求(tools)：model=$model，消息=${messages.size}，工具=${tools.joinToString { it.name }}" }
        val turn = parseTurn(post(buildRequestBody(model, messages, tools)))
        logTurn("tools", turn)
        return turn
    }

    override suspend fun chatStream(
        messages: List<ChatMessage>,
        tools: List<ToolSpec>,
        onDelta: (String) -> Unit
    ): LlmTurn {
        val model = modelProvider()
        logger.d { "AI 请求(stream)：model=$model，消息=${messages.size}，工具=${tools.joinToString { it.name }}" }
        val turn = postStream(buildRequestBody(model, messages, tools, stream = true), onDelta)
        logTurn("stream", turn)
        return turn
    }

    private fun logTurn(via: String, turn: LlmTurn) {
        when (turn) {
            is LlmTurn.Final -> logger.d { "AI 响应($via)：最终文本 len=${turn.text.length}" }
            is LlmTurn.ToolInvocations ->
                logger.d { "AI 响应($via)：请求调用工具 ${turn.calls.joinToString { "${it.name}${AgentLogger.brief(it.argumentsJson, 120)}" }}" }
        }
    }

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
            logger.w { "AI 调用失败：HTTP $code ${AgentLogger.brief(text, 200)}" }
            throw AiException("DeepSeek 调用失败（HTTP $code）：${text.take(300)}")
        }
        return text
    }

    /**
     * 发起流式请求（SSE）：逐行读取 `data:` 事件，文本增量经 [StreamingTextBuffer] 安全透出，
     * 工具调用增量按 index 拼接。返回一轮的最终结果（工具调用或最终文本）。
     */
    private suspend fun postStream(bodyJson: String, onDelta: (String) -> Unit): LlmTurn {
        val apiKey = apiKeyProvider().trim()
        if (apiKey.isEmpty()) throw AiException("未配置 API Key，请在设置中填写")

        val endpoint = baseUrlProvider().trim().trimEnd('/') + "/chat/completions"
        val request = Request.Builder()
            .url(endpoint)
            .header("Authorization", "Bearer $apiKey")
            .header("Content-Type", "application/json")
            .header("Accept", "text/event-stream")
            .post(bodyJson.toRequestBody(JSON_MEDIA_TYPE))
            .build()

        val buffer = StreamingTextBuffer(onDelta)
        val toolAcc = ToolCallAccumulator()

        withContext(Dispatchers.IO) {
            client.newCall(request).execute().use { resp ->
                if (resp.code !in 200..299) {
                    val err = resp.body?.string().orEmpty()
                    logger.w { "AI 流式调用失败：HTTP ${resp.code} ${AgentLogger.brief(err, 200)}" }
                    throw AiException("DeepSeek 调用失败（HTTP ${resp.code}）：${err.take(300)}")
                }
                val source = resp.body?.source() ?: throw AiException("DeepSeek 流式响应为空")
                while (true) {
                    val line = source.readUtf8Line() ?: break
                    if (line.isBlank()) continue
                    if (!line.startsWith("data:")) continue
                    val data = line.substring(5).trim()
                    if (data == "[DONE]") break
                    consumeChunk(data, buffer, toolAcc)
                }
            }
        }

        // 结构化 tool_calls 优先；否则看流式文本里是否为内联工具标记。
        toolAcc.build()?.let { if (it.isNotEmpty()) return LlmTurn.ToolInvocations(it) }
        val raw = buffer.finish()
        if (InlineToolCallParser.containsMarkup(raw)) {
            val inline = InlineToolCallParser.parse(raw)
            if (inline.isNotEmpty()) return LlmTurn.ToolInvocations(inline)
        }
        buffer.flushRemaining()
        return LlmTurn.Final(InlineToolCallParser.strip(raw))
    }

    /** 解析单个 SSE data chunk，把 delta.content 喂给缓冲、delta.tool_calls 累积到 [toolAcc]。 */
    private fun consumeChunk(data: String, buffer: StreamingTextBuffer, toolAcc: ToolCallAccumulator) {
        val delta = runCatching {
            JsonSupport.json.parseToJsonElement(data).jsonObject["choices"]?.jsonArray
                ?.firstOrNull()?.jsonObject?.get("delta")?.jsonObject
        }.getOrNull() ?: return

        delta["content"]?.jsonPrimitive?.contentOrNull?.let { buffer.append(it) }
        delta["tool_calls"]?.jsonArray?.forEach { toolAcc.accumulate(it.jsonObject) }
    }

    internal fun buildRequestBody(
        model: String,
        messages: List<ChatMessage>,
        tools: List<ToolSpec>,
        stream: Boolean = false
    ): String {
        val obj = buildJsonObject {
            put("model", model)
            put("stream", stream)
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

        val content = message["content"]?.jsonPrimitive?.contentOrNull.orEmpty()
        // 兜底：部分模型/代理把工具调用当作文本吐进 content（<invoke .../> 伪标记）。
        // 结构化 tool_calls 缺失时尝试从文本解析，避免裸标记直接展示给用户。
        if (InlineToolCallParser.containsMarkup(content)) {
            val inline = InlineToolCallParser.parse(content)
            if (inline.isNotEmpty()) return LlmTurn.ToolInvocations(inline)
        }
        // 即便未识别为工具调用，也剔除可能残留的标记再作为最终文本返回。
        return LlmTurn.Final(InlineToolCallParser.strip(content))
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
        val content = first["message"]?.jsonObject?.get("content")?.jsonPrimitive?.contentOrNull
            ?: throw AiException("DeepSeek 响应缺少 message.content")
        // 普通补全也可能混入内联工具标记（无工具轮时），剔除后再返回。
        return InlineToolCallParser.strip(content).ifEmpty { content }
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
