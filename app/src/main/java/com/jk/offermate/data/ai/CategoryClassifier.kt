package com.jk.offermate.data.ai

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * 题目分类：把**已有分类清单**连同题目交给 LLM，由它为每道题选择已有分类，
 * 或在都不合适时新建一个**粗粒度**分类。取代写死的关键词表——分类是数据，由模型决定。
 *
 * 纯逻辑的 buildMessages/parse 可用 [FakeAiClient] 单测；classify 做一次 LLM 调用。
 */
class CategoryClassifier(private val aiClient: AiClient) {

    fun buildMessages(
        questions: List<AnsweredQuestion>,
        existingCategories: List<String>
    ): List<ChatMessage> {
        val catLine = if (existingCategories.isEmpty()) "（暂无，请自行拟定简洁的粗类目）"
        else existingCategories.joinToString("、")
        val questionBlock = questions.mapIndexed { i, q -> "$i. ${q.question}" }.joinToString("\n")

        val system = """
            你是面试题分类助手。请为每道题目归入一个**粗粒度**类目。
            规则：
            1. **优先复用**下方"已有类目"中的名称，保持一致，不要造近义词。
            2. 只有当没有任何已有类目合适时，才新建一个**简洁**的新类目（如 Android、Java、Kotlin、计算机网络、操作系统、数据结构与算法、数据库、系统设计）。
            3. 避免过细：如 Handler、SharedPreferences、自定义 View 都应归入 Android；HashMap、JVM 归入 Java。
            4. 每道题只给一个类目。
        """.trimIndent()

        val user = buildString {
            append("已有类目：").append(catLine).append("\n\n")
            append("题目：\n").append(questionBlock).append("\n\n")
            append("只输出 JSON 数组：[{\"index\":0,\"category\":\"类目名\"}, ...]")
        }
        return listOf(ChatMessage(Role.SYSTEM, system), ChatMessage(Role.USER, user))
    }

    /** 解析出与题目一一对应的分类（越界/缺失项为空串）。容错，不抛异常。 */
    fun parse(raw: String, size: Int): List<String> {
        val result = MutableList(size) { "" }
        val jsonText = JsonSupport.extractJsonBlock(raw) ?: return result
        val element = runCatching { JsonSupport.json.parseToJsonElement(jsonText) }.getOrNull() ?: return result
        val array: JsonArray = when {
            element is JsonArray -> element
            element is JsonObject && element["results"] is JsonArray -> element["results"]!!.jsonArray
            else -> return result
        }
        array.forEach { item ->
            val obj = item as? JsonObject ?: return@forEach
            val index = obj["index"]?.jsonPrimitive?.intOrNull ?: return@forEach
            val category = obj["category"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
            if (index in 0 until size && category.isNotEmpty()) result[index] = category
        }
        return result
    }

    /** 为题目分配分类；分类失败或空输入时原样返回。 */
    suspend fun classify(
        questions: List<AnsweredQuestion>,
        existingCategories: List<String>
    ): List<AnsweredQuestion> {
        if (questions.isEmpty()) return questions
        val raw = aiClient.chat(buildMessages(questions, existingCategories))
        val categories = parse(raw, questions.size)
        return questions.mapIndexed { i, q ->
            val c = categories.getOrNull(i).orEmpty()
            if (c.isNotBlank()) q.copy(category = c) else q
        }
    }
}
