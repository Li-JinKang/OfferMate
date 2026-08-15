package com.jk.offermate.data.settings

/**
 * 大模型服务商（均为 OpenAI 兼容 /chat/completions）。除自定义外都带默认 baseUrl 与默认模型。
 */
enum class AiProvider(
    val id: String,
    val label: String,
    val baseUrl: String,
    val defaultModel: String
) {
    DEEPSEEK("deepseek", "DeepSeek", "https://api.deepseek.com/", "deepseek-chat"),
    ALIYUN("aliyun", "阿里通义", "https://dashscope.aliyuncs.com/compatible-mode/v1/", "qwen-plus"),
    VOLC("volc", "火山方舟", "https://ark.cn-beijing.volces.com/api/v3/", "doubao-pro-32k"),
    KIMI("kimi", "Kimi", "https://api.moonshot.cn/v1/", "moonshot-v1-8k"),
    GLM("glm", "智谱 GLM", "https://open.bigmodel.cn/api/paas/v4/", "glm-4-flash"),
    CUSTOM("custom", "自定义", "", "");

    val isCustom: Boolean get() = this == CUSTOM

    companion object {
        fun from(id: String?): AiProvider = entries.firstOrNull { it.id == id } ?: DEEPSEEK
    }
}
