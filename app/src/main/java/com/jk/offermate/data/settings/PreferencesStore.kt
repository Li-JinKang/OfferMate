package com.jk.offermate.data.settings

import com.jk.offermate.agent.mcp.McpServerConfig
import kotlinx.coroutines.flow.Flow

/**
 * 非敏感偏好存储：当前启用的服务商、每个服务商的接口地址/模型、全局相关性阈值，以及 MCP 服务器列表。
 */
interface PreferencesStore {
    val activeProviderId: Flow<String>
    val relevanceThreshold: Flow<Int>

    /** 已配置的外部 MCP 服务器列表。 */
    val mcpServers: Flow<List<McpServerConfig>>

    fun model(providerId: String): Flow<String>
    fun baseUrl(providerId: String): Flow<String>

    suspend fun setActiveProvider(providerId: String)
    suspend fun setModel(providerId: String, model: String)
    suspend fun setBaseUrl(providerId: String, url: String)
    suspend fun setRelevanceThreshold(value: Int)
    suspend fun setMcpServers(servers: List<McpServerConfig>)
}
