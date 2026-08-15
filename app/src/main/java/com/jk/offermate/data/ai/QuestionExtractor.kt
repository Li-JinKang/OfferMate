package com.jk.offermate.data.ai

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

/**
 * AI 分析流水线第一步：从面经帖子正文中**抽取**离散的面试题。
 *
 * 纯逻辑（构造 Prompt + 解析响应），可用 [FakeAiClient] 在 JVM 单测中确定性验证。
 */
class QuestionExtractor(private val aiClient: AiClient) {

    /** 构造发送给模型的消息。约束模型返回严格 JSON。 */
    fun buildMessages(postText: String): List<ChatMessage> = listOf(
        ChatMessage(
            role = Role.SYSTEM,
            content = """
                你是一名面试题抽取助手。用户会给你一段"面经"帖子的正文，
                其中可能混杂流程吐槽、寒暄、无关内容。请从中**只抽取真正的面试题目**。
                严格要求：
                1. 仅输出 JSON，不要输出任何解释或额外文字。
                2. JSON 结构为：{"questions":[{"question":"题目","tags":["考点"],"source":"原文出处片段"}]}。
                3. 不要臆造帖子中不存在的题目；无法识别到题目时返回 {"questions":[]}。
            """.trimIndent()
        ),
        ChatMessage(
            role = Role.USER,
            content = "面经正文如下：\n\n$postText"
        )
    )

    /** 抽取入口：调用模型并解析结果。 */
    suspend fun extract(postText: String): List<ExtractedQuestion> {
        val raw = aiClient.chat(buildMessages(postText))
        return parse(raw)
    }

    /**
     * 解析模型返回的原始文本为题目列表。
     * 容错：兼容被 ```json``` 包裹、前后有多余文字、直接返回数组或返回 {"questions":[...]}。
     */
    fun parse(raw: String): List<ExtractedQuestion> {
        val jsonText = extractJsonBlock(raw)
            ?: throw AiException("模型输出中未找到有效 JSON：${raw.take(200)}")

        val element: JsonElement = try {
            Json.parseToJsonElement(jsonText)
        } catch (e: Exception) {
            throw AiException("JSON 解析失败：${jsonText.take(200)}", e)
        }

        val array: JsonArray = when {
            element is JsonArray -> element
            element is JsonObject && element["questions"] is JsonArray ->
                element["questions"]!!.jsonArray
            else -> throw AiException("JSON 结构不符合预期（缺少 questions 数组）")
        }

        return array.mapNotNull { item ->
            val obj = item as? JsonObject ?: return@mapNotNull null
            val question = obj["question"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
            if (question.isEmpty()) return@mapNotNull null
            val tags = (obj["tags"] as? JsonArray)
                ?.mapNotNull { it.jsonPrimitive.contentOrNull?.trim()?.takeIf(String::isNotEmpty) }
                ?: emptyList()
            val source = (obj["source"] ?: obj["sourceSnippet"])
                ?.jsonPrimitive?.contentOrNull?.trim()?.takeIf(String::isNotEmpty)
            ExtractedQuestion(question = question, tags = tags, sourceSnippet = source)
        }
    }

    /** 从原始文本中截取 JSON 片段（优先取 ``` 代码块，其次取首个 { 或 [ 到匹配的收尾符号）。 */
    private fun extractJsonBlock(raw: String): String? {
        val fenced = FENCE_REGEX.find(raw)?.groupValues?.getOrNull(1)?.trim()
        val candidate = if (!fenced.isNullOrEmpty()) fenced else raw

        val start = candidate.indexOfFirst { it == '{' || it == '[' }
        if (start < 0) return null
        val close = if (candidate[start] == '{') '}' else ']'
        val end = candidate.lastIndexOf(close)
        if (end <= start) return null
        return candidate.substring(start, end + 1)
    }

    companion object {
        private val FENCE_REGEX = Regex("```(?:json)?\\s*([\\s\\S]*?)```", RegexOption.IGNORE_CASE)
    }
}
