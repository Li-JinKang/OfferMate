package com.jk.offermate.data.settings

import kotlinx.coroutines.flow.Flow

/**
 * 非敏感偏好存储：当前启用的服务商、每个服务商的接口地址/模型，以及全局相关性阈值。
 */
interface PreferencesStore {
    val activeProviderId: Flow<String>
    val relevanceThreshold: Flow<Int>

    fun model(providerId: String): Flow<String>
    fun baseUrl(providerId: String): Flow<String>

    suspend fun setActiveProvider(providerId: String)
    suspend fun setModel(providerId: String, model: String)
    suspend fun setBaseUrl(providerId: String, url: String)
    suspend fun setRelevanceThreshold(value: Int)
}
