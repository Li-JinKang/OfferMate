package com.jk.offermate.agent

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

/**
 * AI 分析流水线第二步：结合简历画像，给每道题打相关性分并筛选/排序。
 */
class RelevanceMatcher(
    private val aiClient: AiClient,
    private val toolCallingLlm: ToolCallingLlm? = null,
    private val toolRegistry: ToolRegistry = ToolRegistry(),
    private val maxSteps: Int = 5
) {

    private val toolsEnabled: Boolean
        get() = toolCallingLlm != null && !toolRegistry.isEmpty()

    fun buildMessages(questions: List<ExtractedQuestion>): List<ChatMessage> {
        val questionsBlock = questions
            .mapIndexed { index, q -> "$index. ${q.question}" }
            .joinToString("\n")

        return listOf(
            ChatMessage(
                role = Role.SYSTEM,
                content = """
                    你是一名面试题相关性评估助手。请结合候选人的简历背景，评估每道题与其
                    求职方向/技能/项目的相关程度。
                    获取背景的方式（按需分级调用工具，不要臆造）：
                    1. 先调用 list_memory_profiles 查看候选人有哪些求职方向记忆，选择与题目最相关的一份。
                    2. 用 load_profile_overview(profileId) 加载该方向的技能与项目/经历概览。
                    3. 如需某段项目/经历细节，再用 load_project_detail / load_experience_detail 下钻。
                    若没有任何记忆，则仅按题目本身的通用性给出保守评分。
                    严格要求：
                    1. 最终仅输出 JSON，不要输出任何解释或额外文字。
                    2. 结构为：{"results":[{"index":题目序号,"score":0到100的整数,"reason":"理由","matchedSkills":["命中的技能"]}]}。
                    3. index 必须与输入题目的序号一一对应；分数越高表示越相关。
                """.trimIndent()
            ),
            ChatMessage(
                role = Role.USER,
                content = buildString {
                    append("题目列表：\n")
                    append(questionsBlock)
                }
            )
        )
    }

    /**
     * 评估相关性并筛选。
     * @param threshold 相关性阈值（含），低于该分被过滤。
     * @return 达标题目，按相关性降序。
     */
    suspend fun match(
        questions: List<ExtractedQuestion>,
        threshold: Int = DEFAULT_THRESHOLD
    ): List<RelevanceResult> {
        if (questions.isEmpty()) return emptyList()
        val raw = runTurn(buildMessages(questions))
        return parse(raw, questions)
            .filter { it.score >= threshold }
            .sortedByDescending { it.score }
    }

    /** 有工具则走 agent 工具轮（模型可按需调用记忆工具），否则退回普通补全。 */
    private suspend fun runTurn(messages: List<ChatMessage>): String =
        if (toolsEnabled) {
            ToolCallingAgent(toolCallingLlm!!, toolRegistry, maxSteps).run(messages)
        } else {
            aiClient.chat(messages)
        }

    fun parse(raw: String, questions: List<ExtractedQuestion>): List<RelevanceResult> {
        val jsonText = JsonSupport.extractJsonBlock(raw)
            ?: throw AiException("模型输出中未找到有效 JSON：${raw.take(200)}")

        val element = try {
            JsonSupport.json.parseToJsonElement(jsonText)
        } catch (e: Exception) {
            throw AiException("JSON 解析失败：${jsonText.take(200)}", e)
        }

        val array: JsonArray = when {
            element is JsonArray -> element
            element is JsonObject && element["results"] is JsonArray -> element["results"]!!.jsonArray
            else -> throw AiException("JSON 结构不符合预期（缺少 results 数组）")
        }

        return array.mapNotNull { item ->
            val obj = item as? JsonObject ?: return@mapNotNull null
            val index = obj["index"]?.jsonPrimitive?.intOrNull ?: return@mapNotNull null
            val question = questions.getOrNull(index) ?: return@mapNotNull null
            val score = (obj["score"]?.jsonPrimitive?.intOrNull ?: 0).coerceIn(0, 100)
            val reason = obj["reason"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
            val matchedSkills = (obj["matchedSkills"] as? JsonArray)
                ?.mapNotNull { it.jsonPrimitive.contentOrNull?.trim()?.takeIf(String::isNotEmpty) }
                ?: emptyList()
            RelevanceResult(question = question, score = score, reason = reason, matchedSkills = matchedSkills)
        }
    }

    companion object {
        const val DEFAULT_THRESHOLD = 60
    }
}
