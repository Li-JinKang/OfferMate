package com.jk.offermate.data.settings

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine

/**
 * 设置仓库：聚合敏感 Key（[SecureKeyStore]）与普通偏好（[PreferencesStore]），
 * 对外暴露统一的 [AppSettings] 响应式流。
 *
 * 仅依赖两个抽象，组合逻辑可在 JVM 单测中用内存实现完整验证。
 */
interface SettingsRepository {
    val settings: Flow<AppSettings>

    suspend fun updateApiKey(key: String)
    suspend fun updateModel(model: String)
    suspend fun updateRelevanceThreshold(value: Int)
}

class DefaultSettingsRepository(
    private val secureKeyStore: SecureKeyStore,
    private val preferencesStore: PreferencesStore
) : SettingsRepository {

    // Key 存储本身非响应式，用一个内部流镜像其变化，触发 settings 刷新。
    private val apiKeyFlow = MutableStateFlow(secureKeyStore.getDeepSeekApiKey())

    override val settings: Flow<AppSettings> =
        combine(apiKeyFlow, preferencesStore.model, preferencesStore.relevanceThreshold) { key, model, threshold ->
            AppSettings(
                deepSeekApiKey = key,
                model = model,
                relevanceThreshold = AppSettings.clampThreshold(threshold)
            )
        }

    override suspend fun updateApiKey(key: String) {
        val trimmed = key.trim()
        secureKeyStore.setDeepSeekApiKey(trimmed)
        apiKeyFlow.value = trimmed
    }

    override suspend fun updateModel(model: String) {
        preferencesStore.setModel(model)
    }

    override suspend fun updateRelevanceThreshold(value: Int) {
        preferencesStore.setRelevanceThreshold(AppSettings.clampThreshold(value))
    }
}
