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

    // 只匹配显式的 ```json 围栏；不匹配 ```java 等其它语言围栏，
    // 避免把"答案里的代码块"误当成 JSON 抓出来。
    private val JSON_FENCE = Regex("```json\\s*([\\s\\S]*?)```", RegexOption.IGNORE_CASE)

    /**
     * 从模型原始文本中截取 JSON 片段。
     *
     * 策略（依次尝试，取第一个能成功解析的）：
     * 1. ```json 围栏内的内容；
     * 2. 整段原文。
     * 每个候选都用**尊重字符串引号/转义的配平扫描**定位首个 `{`/`[` 到其匹配收尾符号，
     * 这样答案字符串里内嵌的代码（含 `{}`、```、换行）不会破坏结构定位。
     */
    fun extractJsonBlock(raw: String): String? {
        val candidates = buildList {
            JSON_FENCE.find(raw)?.groupValues?.getOrNull(1)?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?.let { add(it) }
            add(raw)
        }

        // 优先返回"能解析"的块
        for (candidate in candidates) {
            val block = scanBalanced(candidate) ?: continue
            if (isParseable(block)) return block
        }
        // 兜底：返回首个能扫到的块（即使不合法），让上层错误信息能展示实际内容
        for (candidate in candidates) {
            scanBalanced(candidate)?.let { return it }
        }
        return null
    }

    private fun isParseable(block: String): Boolean =
        runCatching { json.parseToJsonElement(block) }.isSuccess

    /** 从首个 `{`/`[` 起，按引号/转义感知的配平扫描，返回到匹配收尾符的子串。 */
    private fun scanBalanced(s: String): String? {
        val start = s.indexOfFirst { it == '{' || it == '[' }
        if (start < 0) return null
        val open = s[start]
        val close = if (open == '{') '}' else ']'
        var depth = 0
        var inString = false
        var escaped = false
        var i = start
        while (i < s.length) {
            val c = s[i]
            if (inString) {
                when {
                    escaped -> escaped = false
                    c == '\\' -> escaped = true
                    c == '"' -> inString = false
                }
            } else {
                when (c) {
                    '"' -> inString = true
                    open -> depth++
                    close -> {
                        depth--
                        if (depth == 0) return s.substring(start, i + 1)
                    }
                }
            }
            i++
        }
        return null
    }
}
