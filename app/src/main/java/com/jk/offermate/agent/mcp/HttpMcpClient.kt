package com.jk.offermate.agent.mcp

import com.jk.offermate.agent.JsonSupport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

/**
 * MCP 客户端的 Streamable HTTP 传输实现（JSON-RPC 2.0）。
 *
 * 单个 [serverUrl] 端点：所有请求 POST 到该 URL。响应可能是 `application/json`（单条）
 * 或 `text/event-stream`（SSE，取其中的 `data:` 负载）。首次调用时完成 `initialize` 握手，
 * 并记住服务器返回的 `Mcp-Session-Id`，后续请求带上。
 *
 * 解析逻辑（[extractResult]/[extractToolText] 等）为纯函数，便于 JVM 单测；
 * 网络部分用注入的 [client] + [serverUrl]（可对接 MockWebServer）。
 */
class HttpMcpClient(
    private val serverUrl: String,
    private val headers: Map<String, String> = emptyMap(),
    private val client: OkHttpClient = defaultClient()
) : McpClient {

    private val idGen = AtomicLong(0)
    private val initMutex = Mutex()

    @Volatile private var initialized = false
    @Volatile private var sessionId: String? = null

    override suspend fun listTools(): List<McpToolSpec> {
        ensureInitialized()
        val result = rpc("tools/list", buildJsonObject { })
        val tools = result["tools"]?.jsonArray ?: return emptyList()
        return tools.mapNotNull { el ->
            val obj = el as? JsonObject ?: return@mapNotNull null
            val name = obj["name"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
                ?: return@mapNotNull null
            McpToolSpec(
                name = name,
                description = obj["description"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                inputSchemaJson = obj["inputSchema"]?.let { it.toString() }.orEmpty()
            )
        }
    }

    override suspend fun callTool(name: String, argumentsJson: String): String {
        ensureInitialized()
        val params = buildJsonObject {
            put("name", name)
            put("arguments", parseArguments(argumentsJson))
        }
        val result = rpc("tools/call", params)
        return extractToolText(result)
    }

    private suspend fun ensureInitialized() {
        if (initialized) return
        initMutex.withLock {
            if (initialized) return
            val params = buildJsonObject {
                put("protocolVersion", PROTOCOL_VERSION)
                putJsonObject("capabilities") { }
                putJsonObject("clientInfo") {
                    put("name", "OfferMate")
                    put("version", "1.0")
                }
            }
            rpc("initialize", params)
            // 通知服务器初始化完成（无需响应）。
            runCatching { notify("notifications/initialized") }
            initialized = true
        }
    }

    /** 发送一条带 id 的 JSON-RPC 请求，返回其 `result` 对象；`error` 或非 2xx 抛 [McpException]。 */
    private suspend fun rpc(method: String, params: JsonObject): JsonObject {
        val id = idGen.incrementAndGet()
        val body = buildJsonObject {
            put("jsonrpc", "2.0")
            put("id", id)
            put("method", method)
            put("params", params)
        }.toString()

        val (code, text, session) = execute(body)
        session?.let { sessionId = it }
        if (code !in 200..299) {
            throw McpException("MCP 调用失败（HTTP $code, method=$method）：${text.take(300)}")
        }
        val payload = extractJsonRpcPayload(text)
            ?: throw McpException("无法解析 MCP 响应（method=$method）：${text.take(300)}")
        payload["error"]?.let { err ->
            val msg = (err as? JsonObject)?.get("message")?.jsonPrimitive?.contentOrNull ?: err.toString()
            throw McpException("MCP 服务器返回错误（method=$method）：$msg")
        }
        return payload["result"] as? JsonObject
            ?: throw McpException("MCP 响应缺少 result（method=$method）：${text.take(200)}")
    }

    /** 发送一条 JSON-RPC 通知（无 id、不期待响应）。 */
    private suspend fun notify(method: String) {
        val body = buildJsonObject {
            put("jsonrpc", "2.0")
            put("method", method)
            put("params", buildJsonObject { })
        }.toString()
        execute(body)
    }

    private suspend fun execute(bodyJson: String): Triple<Int, String, String?> {
        val builder = Request.Builder()
            .url(serverUrl.trim())
            .header("Content-Type", "application/json")
            .header("Accept", "application/json, text/event-stream")
            .post(bodyJson.toRequestBody(JSON_MEDIA_TYPE))
        sessionId?.let { builder.header("Mcp-Session-Id", it) }
        headers.forEach { (k, v) -> builder.header(k, v) }

        return withContext(Dispatchers.IO) {
            client.newCall(builder.build()).execute().use { resp ->
                Triple(resp.code, resp.body?.string().orEmpty(), resp.header("Mcp-Session-Id"))
            }
        }
    }

    private companion object {
        const val PROTOCOL_VERSION = "2025-06-18"
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .callTimeout(90, TimeUnit.SECONDS)
            .build()
    }
}

/**
 * 从响应体中取出 JSON-RPC 负载对象：支持直接 JSON，或 SSE（取 `data:` 行拼成的 JSON）。
 * 纯函数，便于单测。
 */
internal fun extractJsonRpcPayload(body: String): JsonObject? {
    val trimmed = body.trim()
    if (trimmed.isEmpty()) return null

    // 直接 JSON
    if (trimmed.startsWith("{")) {
        return runCatching { JsonSupport.json.parseToJsonElement(trimmed).jsonObject }.getOrNull()
    }

    // SSE：拼接所有 data: 行（同一事件可能多行），取最后一个能解析为 JSON-RPC 的对象
    val dataLines = trimmed.lineSequence()
        .map { it.trimEnd('\r') }
        .filter { it.startsWith("data:") }
        .map { it.removePrefix("data:").trim() }
        .filter { it.isNotEmpty() }
        .toList()

    // 优先整体拼接；失败则逐条尝试
    val joined = dataLines.joinToString("\n")
    runCatching { JsonSupport.json.parseToJsonElement(joined).jsonObject }
        .getOrNull()?.let { if (it.containsKey("jsonrpc") || it.containsKey("result") || it.containsKey("error")) return it }

    for (line in dataLines.asReversed()) {
        val obj = runCatching { JsonSupport.json.parseToJsonElement(line).jsonObject }.getOrNull()
        if (obj != null) return obj
    }
    return null
}

/** 从 tools/call 的 result 提取文本：拼接 content 数组里各 text 块；标注 isError。 */
internal fun extractToolText(result: JsonObject): String {
    val content = result["content"]?.jsonArray
    val text = content
        ?.mapNotNull { el ->
            val obj = el as? JsonObject ?: return@mapNotNull null
            when (obj["type"]?.jsonPrimitive?.contentOrNull) {
                "text" -> obj["text"]?.jsonPrimitive?.contentOrNull
                else -> obj["text"]?.jsonPrimitive?.contentOrNull // 尽力取 text 字段
            }
        }
        ?.filter { it.isNotBlank() }
        ?.joinToString("\n")
        .orEmpty()

    val body = text.ifBlank { result.toString() }
    val isError = result["isError"]?.jsonPrimitive?.contentOrNull == "true"
    return if (isError) "（工具返回错误）$body" else body
}

/** 把工具参数 JSON 字符串解析为对象；非法/空则回退空对象。 */
internal fun parseArguments(argumentsJson: String): JsonObject =
    runCatching { JsonSupport.json.parseToJsonElement(argumentsJson).jsonObject }
        .getOrDefault(buildJsonObject { })
