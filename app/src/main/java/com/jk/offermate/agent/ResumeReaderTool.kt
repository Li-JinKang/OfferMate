package com.jk.offermate.agent

import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * 首个本地工具：按需读取候选人简历。
 *
 * 用途：让"首屏只带最小画像"成为可能——模型判断信息不足时，调用本工具拉取简历（可带 query 过滤），
 * 而不是每次把整份简历塞进 Prompt。通过 [resumeTextProvider] 注入简历来源，与存储解耦、便于测试。
 */
class ResumeReaderTool(
    private val resumeTextProvider: suspend () -> String,
    private val maxChars: Int = 4000
) : Tool {

    override val spec = ToolSpec(
        name = "read_resume",
        description = "读取候选人简历全文，用于判断题目与其技术栈/项目的相关性，或结合其经历作答。" +
            "可传 query 关键词，仅返回包含该词的相关段落。",
        parametersJson = """
            {"type":"object","properties":{"query":{"type":"string","description":"可选关键词，仅返回相关段落"}}}
        """.trimIndent()
    )

    override suspend fun call(argumentsJson: String): String {
        val text = resumeTextProvider().trim()
        if (text.isEmpty()) return "（候选人尚未上传简历）"

        val query = parseQuery(argumentsJson)
        if (query.isNullOrBlank()) return text.take(maxChars)

        val hits = text.lines().filter { it.contains(query, ignoreCase = true) }
        return (if (hits.isEmpty()) text else hits.joinToString("\n")).take(maxChars)
    }

    private fun parseQuery(argumentsJson: String): String? = runCatching {
        JsonSupport.json.parseToJsonElement(argumentsJson)
            .jsonObject["query"]?.jsonPrimitive?.contentOrNull
    }.getOrNull()
}
