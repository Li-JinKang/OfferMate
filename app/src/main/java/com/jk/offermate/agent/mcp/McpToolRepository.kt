package com.jk.offermate.agent.mcp

import com.jk.offermate.agent.Tool

/**
 * 发现并缓存所有已配置 MCP 服务器的工具，向工具轮暴露为本地 [Tool] 列表。
 *
 * [current] 返回上次 [refresh] 发现的工具（供 `ToolRegistry` 的 provider 读取，
 * 因此新发现的工具会自动出现在后续工具轮）。发现是 **best-effort**：某台服务器
 * 连接/列举失败只跳过该台，不影响其余与本地工具。
 *
 * 依赖以函数注入，便于 JVM 单测（fake [McpClient]）：
 * @param serversProvider 读取当前启用的服务器配置。
 * @param clientFactory   由配置构造 [McpClient]（默认 [HttpMcpClient]）。
 */
class McpToolRepository(
    private val serversProvider: suspend () -> List<McpServerConfig>,
    private val clientFactory: (McpServerConfig) -> McpClient = { HttpMcpClient(it.url, it.headers) }
) {
    @Volatile
    private var cache: List<Tool> = emptyList()

    /** 上次刷新发现的工具（未刷新则为空）。 */
    fun current(): List<Tool> = cache

    /** 重新连接所有启用的服务器并刷新工具缓存。返回本次发现的工具数。 */
    suspend fun refresh(): Int {
        val servers = serversProvider().filter { it.enabled && it.url.isNotBlank() }
        val tools = mutableListOf<Tool>()
        val usedNames = mutableSetOf<String>()

        for (server in servers) {
            val client = clientFactory(server)
            val specs = runCatching { client.listTools() }.getOrElse { emptyList() }
            for (spec in specs) {
                val exposed = uniqueName(server.name, spec.name, usedNames)
                tools += McpTool(
                    client = client,
                    originalName = spec.name,
                    exposedName = exposed,
                    description = spec.description,
                    parametersJson = spec.inputSchemaJson
                )
            }
        }
        cache = tools
        return tools.size
    }

    private fun uniqueName(serverName: String, toolName: String, used: MutableSet<String>): String {
        val base = sanitizeToolName("mcp_${serverName}_$toolName")
        var candidate = base
        var i = 1
        while (!used.add(candidate)) {
            candidate = sanitizeToolName("${base.take(60)}_$i")
            i++
        }
        return candidate
    }
}

/** OpenAI function 名约束：`^[a-zA-Z0-9_-]{1,64}$`。非法字符替换为下划线并截断。 */
internal fun sanitizeToolName(raw: String): String {
    val cleaned = raw.map { c ->
        if (c.isLetterOrDigit() || c == '_' || c == '-') c else '_'
    }.joinToString("").ifEmpty { "tool" }
    return cleaned.take(64)
}
