package com.jk.offermate.data.ai

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

/**
 * AI 分析流水线第三步：为筛选出的相关题目生成参考答案。
 */
class AnswerGenerator(private val aiClient: AiClient) {

    fun buildMessages(
        relevant: List<RelevanceResult>,
        profile: ResumeProfile
    ): List<ChatMessage> {
        val questionsBlock = relevant
            .mapIndexed { index, r -> "$index. ${r.question.question}" }
            .joinToString("\n")

        return listOf(
            ChatMessage(
                role = Role.SYSTEM,
                content = """
                    你是一名资深面试辅导老师。请结合候选人的简历画像，为每道题给出**参考答案**、
                    难度评级与关键要点，可适当结合其项目经历给出作答建议。
                    严格要求：
                    1. 仅输出 JSON，不要输出任何解释或额外文字。
                    2. 结构为：{"answers":[{"index":题目序号,"answer":"参考答案","difficulty":"easy|medium|hard","keyPoints":["要点"]}]}。
                    3. index 必须与输入题目的序号一一对应。
                """.trimIndent()
            ),
            ChatMessage(
                role = Role.USER,
                content = buildString {
                    append("候选人简历画像：\n")
                    append("目标岗位：${profile.targetRole}\n")
                    if (profile.skills.isNotEmpty()) append("技能：${profile.skills.joinToString("、")}\n")
                    if (profile.projects.isNotEmpty()) append("项目：${profile.projects.joinToString("、")}\n")
                    append("\n待作答题目：\n")
                    append(questionsBlock)
                }
            )
        )
    }

    suspend fun answer(
        relevant: List<RelevanceResult>,
        profile: ResumeProfile
    ): List<AnsweredQuestion> {
        if (relevant.isEmpty()) return emptyList()
        val raw = aiClient.chat(buildMessages(relevant, profile))
        return parse(raw, relevant)
    }

    fun parse(raw: String, relevant: List<RelevanceResult>): List<AnsweredQuestion> {
        val jsonText = JsonSupport.extractJsonBlock(raw)
            ?: throw AiException("模型输出中未找到有效 JSON：${raw.take(200)}")

        val element = try {
            JsonSupport.json.parseToJsonElement(jsonText)
        } catch (e: Exception) {
            throw AiException("JSON 解析失败：${jsonText.take(200)}", e)
        }

        val array: JsonArray = when {
            element is JsonArray -> element
            element is JsonObject && element["answers"] is JsonArray -> element["answers"]!!.jsonArray
            else -> throw AiException("JSON 结构不符合预期（缺少 answers 数组）")
        }

        return array.mapNotNull { item ->
            val obj = item as? JsonObject ?: return@mapNotNull null
            val index = obj["index"]?.jsonPrimitive?.intOrNull ?: return@mapNotNull null
            val relevanceResult = relevant.getOrNull(index) ?: return@mapNotNull null
            val answer = obj["answer"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
            if (answer.isEmpty()) return@mapNotNull null
            val difficulty = Difficulty.from(obj["difficulty"]?.jsonPrimitive?.contentOrNull)
            val keyPoints = (obj["keyPoints"] as? JsonArray)
                ?.mapNotNull { it.jsonPrimitive.contentOrNull?.trim()?.takeIf(String::isNotEmpty) }
                ?: emptyList()
            AnsweredQuestion(
                question = relevanceResult.question.question,
                answer = answer,
                tags = relevanceResult.question.tags,
                difficulty = difficulty,
                keyPoints = keyPoints,
                relevanceScore = relevanceResult.score,
                relevanceReason = relevanceResult.reason
            )
        }
    }
}
