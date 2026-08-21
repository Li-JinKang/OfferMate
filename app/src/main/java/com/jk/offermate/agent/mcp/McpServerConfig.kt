package com.jk.offermate.agent.mcp

/**
 * 一台用户配置的外部 MCP 服务器。
 *
 * @param name 展示名（也用于工具命名空间前缀，避免多服务器/本地工具重名）。
 * @param url  Streamable HTTP 端点。
 * @param headers 额外请求头（如鉴权 `Authorization: Bearer ...`）。
 * @param enabled 是否启用（关闭则不发现其工具）。
 */
data class McpServerConfig(
    val name: String,
    val url: String,
    val headers: Map<String, String> = emptyMap(),
    val enabled: Boolean = true
)
