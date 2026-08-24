package com.jk.offermate.agent.pipeline

import com.jk.offermate.agent.AiClient
import com.jk.offermate.agent.AiException
import com.jk.offermate.agent.ChatMessage
import com.jk.offermate.agent.JsonSupport
import com.jk.offermate.agent.Role
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
                你需要从用户给出的一段"面经"帖子的正文中抽取**真正的面试题目**
                其中可能混杂流程吐槽、寒暄、无关内容。
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
     *
     * 降级策略：若整体 JSON 无法解析（输出超长被截断等），尝试从 `questions` 数组里
     * 抢救已完整闭合的对象，避免"最后一题没写完 → 整批抽题结果全丢"。仅当抢救结果也为空时才抛异常。
     */
    fun parse(raw: String): List<ExtractedQuestion> {
        val jsonText = JsonSupport.extractJsonBlock(raw)
        val array: JsonArray? = jsonText
            ?.let { runCatching { JsonSupport.json.parseToJsonElement(it) }.getOrNull() }
            ?.let { element: JsonElement ->
                when {
                    element is JsonArray -> element
                    element is JsonObject && element["questions"] is JsonArray -> element["questions"]!!.jsonArray
                    else -> null
                }
            }

        // array != null 说明整体 JSON 解析成功（哪怕数组为空，也是模型明确表示"无题目"，不算失败）。
        // array == null 才是整体解析失败，此时才尝试逐对象抢救；抢救结果也为空才真正报错。
        val objects: List<JsonObject> = array?.filterIsInstance<JsonObject>()
            ?: JsonSupport.salvageObjects(raw, "questions").ifEmpty {
                throw AiException("模型输出中未找到有效 JSON：${raw.take(200)}")
            }

        return objects.mapNotNull { obj ->
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
}
