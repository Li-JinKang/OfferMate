package com.jk.offermate.agent.mcp

/** 外部 MCP 服务器暴露的一个工具描述。 */
data class McpToolSpec(
    val name: String,
    val description: String,
    /** inputSchema（JSON Schema 字符串），可直接作为 OpenAI function 的 parameters。 */
    val inputSchemaJson: String
)

/**
 * MCP（Model Context Protocol）客户端端口：连接一台外部工具服务器，
 * 列出其工具并按需调用。传输实现见 [HttpMcpClient]（JSON-RPC over Streamable HTTP）。
 */
interface McpClient {
    /** 列出服务器工具（内部按需完成 initialize 握手）。 */
    suspend fun listTools(): List<McpToolSpec>

    /** 调用某工具，返回文本化结果。 */
    suspend fun callTool(name: String, argumentsJson: String): String
}

/** MCP 相关异常（握手/传输/协议错误）。 */
class McpException(message: String, cause: Throwable? = null) : Exception(message, cause)
