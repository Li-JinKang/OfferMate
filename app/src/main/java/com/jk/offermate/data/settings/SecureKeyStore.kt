package com.jk.offermate.data.settings

/**
 * 敏感凭据的安全存储抽象（如 DeepSeek API Key）。
 * 抽象出来便于在 JVM 单测中用内存实现替换，无需真实加密/Android 环境。
 */
interface SecureKeyStore {
    fun getDeepSeekApiKey(): String
    fun setDeepSeekApiKey(key: String)
}
