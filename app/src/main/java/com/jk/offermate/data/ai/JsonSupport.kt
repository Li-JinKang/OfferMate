package com.jk.offermate.data.ai

import kotlinx.serialization.json.Json

/**
 * LLM 输出解析的共享工具（供抽题/相关性/作答三步复用）。
 */
internal object JsonSupport {

    val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val FENCE = Regex("```(?:json)?\\s*([\\s\\S]*?)```", RegexOption.IGNORE_CASE)

    /**
     * 从模型原始文本中截取 JSON 片段：优先取 ``` 代码块，其次取首个 { 或 [ 到匹配的收尾符号。
     */
    fun extractJsonBlock(raw: String): String? {
        val fenced = FENCE.find(raw)?.groupValues?.getOrNull(1)?.trim()
        val candidate = if (!fenced.isNullOrEmpty()) fenced else raw

        val start = candidate.indexOfFirst { it == '{' || it == '[' }
        if (start < 0) return null
        val close = if (candidate[start] == '{') '}' else ']'
        val end = candidate.lastIndexOf(close)
        if (end <= start) return null
        return candidate.substring(start, end + 1)
    }
}
