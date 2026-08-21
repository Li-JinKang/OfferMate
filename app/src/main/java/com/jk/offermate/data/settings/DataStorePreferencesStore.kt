package com.jk.offermate.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.jk.offermate.agent.mcp.McpServerConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "offermate_settings")

/**
 * 基于 DataStore 的偏好存储：接口地址/模型按服务商 id 分键存储。
 */
class DataStorePreferencesStore(context: Context) : PreferencesStore {

    private val dataStore = context.settingsDataStore

    override val activeProviderId: Flow<String> =
        dataStore.data.map { it[KEY_ACTIVE] ?: AiProvider.DEEPSEEK.id }

    override val relevanceThreshold: Flow<Int> =
        dataStore.data.map { it[KEY_THRESHOLD] ?: AppSettings.DEFAULT_THRESHOLD }

    override val mcpServers: Flow<List<McpServerConfig>> =
        dataStore.data.map { decodeServers(it[KEY_MCP_SERVERS]) }

    override fun model(providerId: String): Flow<String> =
        dataStore.data.map { it[modelKey(providerId)] ?: AiProvider.from(providerId).defaultModel }

    override fun baseUrl(providerId: String): Flow<String> =
        dataStore.data.map { it[baseUrlKey(providerId)] ?: AiProvider.from(providerId).baseUrl }

    override suspend fun setActiveProvider(providerId: String) {
        dataStore.edit { it[KEY_ACTIVE] = providerId }
    }

    override suspend fun setModel(providerId: String, model: String) {
        dataStore.edit { it[modelKey(providerId)] = model }
    }

    override suspend fun setBaseUrl(providerId: String, url: String) {
        dataStore.edit { it[baseUrlKey(providerId)] = url }
    }

    override suspend fun setRelevanceThreshold(value: Int) {
        dataStore.edit { it[KEY_THRESHOLD] = AppSettings.clampThreshold(value) }
    }

    override suspend fun setMcpServers(servers: List<McpServerConfig>) {
        dataStore.edit { it[KEY_MCP_SERVERS] = encodeServers(servers) }
    }

    private fun modelKey(id: String) = stringPreferencesKey("model_$id")
    private fun baseUrlKey(id: String) = stringPreferencesKey("base_$id")

    private companion object {
        val KEY_ACTIVE = stringPreferencesKey("active_provider")
        val KEY_THRESHOLD = intPreferencesKey("relevance_threshold")
        val KEY_MCP_SERVERS = stringPreferencesKey("mcp_servers")
        val json = Json { ignoreUnknownKeys = true }
    }

    /** MCP 服务器列表 ↔ JSON（用 kotlinx-serialization 运行时 API，无需编译器插件）。 */
    private fun encodeServers(servers: List<McpServerConfig>): String =
        buildJsonArray {
            servers.forEach { s ->
                addJsonObject {
                    put("name", s.name)
                    put("url", s.url)
                    put("enabled", s.enabled)
                    putJsonObject("headers") { s.headers.forEach { (k, v) -> put(k, v) } }
                }
            }
        }.toString()

    private fun decodeServers(raw: String?): List<McpServerConfig> {
        if (raw.isNullOrBlank()) return emptyList()
        val array = runCatching { json.parseToJsonElement(raw).jsonArray }.getOrNull() ?: return emptyList()
        return array.mapNotNull { el ->
            val obj = el as? JsonObject ?: return@mapNotNull null
            val name = obj["name"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val url = obj["url"]?.jsonPrimitive?.contentOrNull.orEmpty()
            val enabled = obj["enabled"]?.jsonPrimitive?.booleanOrNull ?: true
            val headers = (obj["headers"] as? JsonObject)?.mapNotNull { (k, v) ->
                (v.jsonPrimitive.contentOrNull)?.let { k to it }
            }?.toMap() ?: emptyMap()
            McpServerConfig(name = name, url = url, headers = headers, enabled = enabled)
        }
    }
}
