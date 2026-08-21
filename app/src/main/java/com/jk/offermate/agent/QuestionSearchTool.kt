package com.jk.offermate.agent

import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * 本地工具：按关键词搜索候选人**已有题库**（题干/答案/标签/考点/分类任一命中）。
 *
 * 让模型能自主"翻阅题库"——例如自由对话时先查是否已有相关题目及其参考答案，
 * 或作答/相关性判断时参考同类历史题。通过 [search] 注入数据来源，与存储解耦、便于测试。
 */
class QuestionSearchTool(
    private val search: suspend (query: String, limit: Int) -> List<AnsweredQuestion>,
    private val defaultLimit: Int = 10,
    private val maxAnswerChars: Int = 400
) : Tool {

    override val spec = ToolSpec(
        name = "search_questions",
        description = "在候选人已有的面试题库中按关键词检索题目（匹配题干/答案/考点/分类）。" +
            "用于查阅是否已有相关题目及其参考答案、了解候选人已积累的考点。返回题目及其答案摘要。",
        parametersJson = """
            {"type":"object","properties":{"query":{"type":"string","description":"检索关键词，如某个技术点/公司/考点"},"limit":{"type":"integer","description":"最多返回条数，默认 $defaultLimit"}},"required":["query"]}
        """.trimIndent()
    )

    override suspend fun call(argumentsJson: String): String {
        val args = runCatching { JsonSupport.json.parseToJsonElement(argumentsJson).jsonObject }.getOrNull()
        val query = args?.get("query")?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
        if (query.isEmpty()) return "（未提供检索关键词 query）"
        val limit = (args?.get("limit")?.jsonPrimitive?.intOrNull ?: defaultLimit).coerceIn(1, 50)

        val hits = search(query, limit)
        if (hits.isEmpty()) return "题库中未找到与「$query」相关的题目。"

        return buildString {
            append("题库中与「$query」相关的题目（共 ${hits.size} 条）：\n")
            hits.forEachIndexed { i, q ->
                append("\n${i + 1}. 【题目】").append(q.question.trim()).append("\n")
                if (q.category.isNotBlank()) append("   【分类】").append(q.category).append("\n")
                val ans = q.answer.trim()
                if (ans.isNotEmpty()) {
                    append("   【参考答案】").append(ans.take(maxAnswerChars))
                    if (ans.length > maxAnswerChars) append("…")
                    append("\n")
                }
            }
        }
    }
}
