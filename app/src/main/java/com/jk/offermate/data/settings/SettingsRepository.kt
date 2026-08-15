package com.jk.offermate.data.settings

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine

/**
 * 设置仓库：聚合敏感 Key（[SecureKeyStore]）与普通偏好（[PreferencesStore]），
 * 对外暴露统一的 [AppSettings] 响应式流。组合逻辑可在 JVM 单测中用内存实现完整验证。
 */
interface SettingsRepository {
    val settings: Flow<AppSettings>

    suspend fun updateApiKey(key: String)
    suspend fun updateModel(model: String)
    suspend fun updateRelevanceThreshold(value: Int)

    /** 切换服务商：非自定义时自动填入默认接口地址与默认模型。 */
    suspend fun updateProvider(provider: AiProvider)

    /** 自定义接口地址。 */
    suspend fun updateBaseUrl(url: String)
}

class DefaultSettingsRepository(
    private val secureKeyStore: SecureKeyStore,
    private val preferencesStore: PreferencesStore
) : SettingsRepository {

    private val apiKeyFlow = MutableStateFlow(secureKeyStore.getDeepSeekApiKey())

    override val settings: Flow<AppSettings> =
        combine(
            apiKeyFlow,
            preferencesStore.model,
            preferencesStore.relevanceThreshold,
            preferencesStore.provider,
            preferencesStore.baseUrl
        ) { key, model, threshold, providerId, baseUrl ->
            AppSettings(
                deepSeekApiKey = key,
                model = model,
                relevanceThreshold = AppSettings.clampThreshold(threshold),
                providerId = providerId,
                baseUrl = baseUrl
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

    override suspend fun updateProvider(provider: AiProvider) {
        preferencesStore.setProvider(provider.id)
        if (!provider.isCustom) {
            preferencesStore.setBaseUrl(provider.baseUrl)
            preferencesStore.setModel(provider.defaultModel)
        }
    }

    override suspend fun updateBaseUrl(url: String) {
        preferencesStore.setBaseUrl(url.trim())
    }
}
