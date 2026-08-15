package com.jk.offermate.data.settings

/**
 * 敏感凭据的安全存储抽象：**每个服务商各自独立的 API Key**。
 * 抽象出来便于在 JVM 单测中用内存实现替换。
 */
interface SecureKeyStore {
    fun getApiKey(providerId: String): String
    fun setApiKey(providerId: String, key: String)
}
