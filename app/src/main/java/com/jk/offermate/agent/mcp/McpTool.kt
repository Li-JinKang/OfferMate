package com.jk.offermate.agent.mcp

import com.jk.offermate.agent.Tool
import com.jk.offermate.agent.ToolSpec

/**
 * 把一个 MCP 服务器工具适配为本地 [Tool]，从而能与本地工具一样进入同一个工具轮。
 *
 * [spec].name 是**命名空间化后的对外名**（供模型调用，保证全局唯一）；调用时转成
 * 服务器上的 [originalName] 经 [client] 转发。
 */
class McpTool(
    private val client: McpClient,
    private val originalName: String,
    exposedName: String,
    description: String,
    parametersJson: String
) : Tool {

    override val spec = ToolSpec(
        name = exposedName,
        description = description,
        parametersJson = parametersJson
    )

    override suspend fun call(argumentsJson: String): String =
        try {
            client.callTool(originalName, argumentsJson)
        } catch (e: McpException) {
            "MCP 工具调用失败：${e.message}"
        }
}
