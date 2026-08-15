package com.jk.offermate.data.settings

import kotlinx.coroutines.flow.Flow

/**
 * 非敏感偏好的响应式存储抽象（服务商、接口地址、模型名、相关性阈值等）。
 * 具体实现基于 DataStore；测试用内存实现替换。
 */
interface PreferencesStore {
    val provider: Flow<String>
    val baseUrl: Flow<String>
    val model: Flow<String>
    val relevanceThreshold: Flow<Int>

    suspend fun setProvider(providerId: String)
    suspend fun setBaseUrl(url: String)
    suspend fun setModel(model: String)
    suspend fun setRelevanceThreshold(value: Int)
}
