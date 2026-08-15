package com.jk.offermate.data.settings

/**
 * 应用设置（BYOK 场景）。API Key 属敏感信息，加密存储；其余为普通偏好。
 */
data class AppSettings(
    val deepSeekApiKey: String = "",
    val model: String = AiProvider.DEEPSEEK.defaultModel,
    val relevanceThreshold: Int = DEFAULT_THRESHOLD,
    val providerId: String = AiProvider.DEEPSEEK.id,
    val baseUrl: String = AiProvider.DEEPSEEK.baseUrl
) {
    /** 是否已配置可用的 API Key。 */
    val isDeepSeekConfigured: Boolean
        get() = deepSeekApiKey.isNotBlank()

    val provider: AiProvider get() = AiProvider.from(providerId)

    companion object {
        const val DEFAULT_THRESHOLD = 60
        const val MIN_THRESHOLD = 0
        const val MAX_THRESHOLD = 100

        fun clampThreshold(value: Int): Int = value.coerceIn(MIN_THRESHOLD, MAX_THRESHOLD)
    }
}
