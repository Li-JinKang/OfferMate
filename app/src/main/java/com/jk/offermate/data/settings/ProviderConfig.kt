package com.jk.offermate.data.settings

/**
 * 某个服务商的配置：各自独立的 API Key、模型、接口地址。
 */
data class ProviderConfig(
    val providerId: String,
    val apiKey: String,
    val model: String,
    val baseUrl: String
) {
    val provider: AiProvider get() = AiProvider.from(providerId)
    val isConfigured: Boolean get() = apiKey.isNotBlank()

    companion object {
        /** 某服务商的默认（未配置）状态。 */
        fun defaultsFor(provider: AiProvider) = ProviderConfig(
            providerId = provider.id,
            apiKey = "",
            model = provider.defaultModel,
            baseUrl = provider.baseUrl
        )
    }
}
