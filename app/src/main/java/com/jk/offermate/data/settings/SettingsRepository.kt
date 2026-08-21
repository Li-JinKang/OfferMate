package com.jk.offermate.data.settings

import com.jk.offermate.agent.mcp.McpServerConfig
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest

/**
 * 设置仓库：每个服务商各自独立的 Key/模型/接口地址，另有一个"当前启用"的服务商。
 * 分析时使用启用服务商的配置；UI 可查看/编辑任一服务商配置。
 */
interface SettingsRepository {
    /** 当前启用的服务商 id。 */
    val activeProviderId: Flow<String>

    /** 全局相关性阈值。 */
    val relevanceThreshold: Flow<Int>

    /** 当前启用服务商的完整配置（供分析使用）。 */
    val activeConfig: Flow<ProviderConfig>

    /** 某服务商的配置（供设置页查看/预填）。 */
    fun config(providerId: String): Flow<ProviderConfig>

    /** 已配置的外部 MCP 服务器列表。 */
    val mcpServers: Flow<List<McpServerConfig>>

    /** 启用某服务商：保存其 Key/模型/接口地址并设为当前启用。 */
    suspend fun enableProvider(providerId: String, apiKey: String, model: String, baseUrl: String)

    suspend fun updateRelevanceThreshold(value: Int)

    /** 新增（或按 name 覆盖）一台 MCP 服务器。 */
    suspend fun addMcpServer(name: String, url: String, headers: Map<String, String> = emptyMap())

    /** 按 name 删除一台 MCP 服务器。 */
    suspend fun removeMcpServer(name: String)

    /** 启用/停用某台 MCP 服务器。 */
    suspend fun setMcpServerEnabled(name: String, enabled: Boolean)
}

@OptIn(ExperimentalCoroutinesApi::class)
class DefaultSettingsRepository(
    private val secureKeyStore: SecureKeyStore,
    private val preferencesStore: PreferencesStore
) : SettingsRepository {

    // Key 存储非响应式，用一个 bump 触发 config 重新读取。
    private val keyBump = MutableStateFlow(0)

    override val activeProviderId: Flow<String> = preferencesStore.activeProviderId

    override val relevanceThreshold: Flow<Int> = preferencesStore.relevanceThreshold

    override fun config(providerId: String): Flow<ProviderConfig> =
        combine(
            preferencesStore.model(providerId),
            preferencesStore.baseUrl(providerId),
            keyBump
        ) { model, baseUrl, _ ->
            ProviderConfig(
                providerId = providerId,
                apiKey = secureKeyStore.getApiKey(providerId),
                model = model,
                baseUrl = baseUrl
            )
        }

    override val activeConfig: Flow<ProviderConfig> =
        preferencesStore.activeProviderId.flatMapLatest { id -> config(id) }

    override val mcpServers: Flow<List<McpServerConfig>> = preferencesStore.mcpServers

    override suspend fun enableProvider(providerId: String, apiKey: String, model: String, baseUrl: String) {
        secureKeyStore.setApiKey(providerId, apiKey.trim())
        preferencesStore.setModel(providerId, model.trim())
        preferencesStore.setBaseUrl(providerId, baseUrl.trim())
        preferencesStore.setActiveProvider(providerId)
        keyBump.value += 1
    }

    override suspend fun updateRelevanceThreshold(value: Int) {
        preferencesStore.setRelevanceThreshold(AppSettings.clampThreshold(value))
    }

    override suspend fun addMcpServer(name: String, url: String, headers: Map<String, String>) {
        val n = name.trim()
        val u = url.trim()
        if (n.isEmpty() || u.isEmpty()) return
        val current = preferencesStore.mcpServers.first()
        val next = current.filterNot { it.name.equals(n, ignoreCase = true) } +
            McpServerConfig(name = n, url = u, headers = headers, enabled = true)
        preferencesStore.setMcpServers(next)
    }

    override suspend fun removeMcpServer(name: String) {
        val current = preferencesStore.mcpServers.first()
        preferencesStore.setMcpServers(current.filterNot { it.name.equals(name.trim(), ignoreCase = true) })
    }

    override suspend fun setMcpServerEnabled(name: String, enabled: Boolean) {
        val current = preferencesStore.mcpServers.first()
        preferencesStore.setMcpServers(
            current.map { if (it.name.equals(name.trim(), ignoreCase = true)) it.copy(enabled = enabled) else it }
        )
    }
}
