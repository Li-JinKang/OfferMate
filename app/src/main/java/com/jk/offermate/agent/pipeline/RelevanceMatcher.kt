package com.jk.offermate.agent.pipeline

import com.jk.offermate.agent.AgentLogger
import com.jk.offermate.agent.AiClient
import com.jk.offermate.agent.AiException
import com.jk.offermate.agent.ChatMessage
import com.jk.offermate.agent.JsonSupport
import com.jk.offermate.agent.NoopAgentLogger
import com.jk.offermate.agent.Role
import com.jk.offermate.agent.tool.ToolCallingAgent
import com.jk.offermate.agent.tool.ToolCallingLlm
import com.jk.offermate.agent.tool.ToolRegistry
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
    private val maxSteps: Int = 15,
    private val logger: AgentLogger = NoopAgentLogger
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
                    重要例外：算法/数据结构（如笔试编程题、LeetCode 风格题）、操作系统、计算机网络
                    等计算机基础题，是几乎所有技术岗位（无论前端/后端/移动端）面试的通用必考内容，
                    即使简历中未直接体现相关技能，也应视为高相关（评分不低于 70），不得仅因简历未提及而打低分。
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
        val results = parse(raw, questions)

        val (kept, dropped) = results.partition { it.score >= threshold }
        if (dropped.isNotEmpty()) {
            logger.d {
                "相关性过滤：丢弃 ${dropped.size}/${results.size} 题（阈值=$threshold）→ " +
                    dropped.joinToString("；") { "[${it.score}分]${AgentLogger.brief(it.question.question, 40)}" }
            }
        }
        return kept.sortedByDescending { it.score }
    }

    /** 有工具则走 agent 工具轮（模型可按需调用记忆工具），否则退回普通补全。 */
    private suspend fun runTurn(messages: List<ChatMessage>): String =
        if (toolsEnabled) {
            ToolCallingAgent(toolCallingLlm!!, toolRegistry, maxSteps, logger).run(messages)
        } else {
            aiClient.chat(messages)
        }

    /**
     * 解析模型返回的相关性打分。
     *
     * 降级策略：若整体 JSON 无法解析（输出超长被截断等），尝试从 `results` 数组里抢救
     * 已完整闭合的对象，避免"最后一条没写完 → 整批相关性判断全丢、题目连同被过滤"。
     * 仅当抢救结果也为空时才抛异常。
     */
    fun parse(raw: String, questions: List<ExtractedQuestion>): List<RelevanceResult> {
        val jsonText = JsonSupport.extractJsonBlock(raw)
        val array: JsonArray? = jsonText
            ?.let { runCatching { JsonSupport.json.parseToJsonElement(it) }.getOrNull() }
            ?.let { element ->
                when {
                    element is JsonArray -> element
                    element is JsonObject && element["results"] is JsonArray -> element["results"]!!.jsonArray
                    else -> null
                }
            }

        // array != null 说明整体 JSON 解析成功（即使数组为空，也是模型的明确结果，不算失败）。
        // array == null 才是整体解析失败，此时才尝试逐对象抢救；抢救结果也为空才真正报错。
        val objects: List<JsonObject> = array?.filterIsInstance<JsonObject>()
            ?: JsonSupport.salvageObjects(raw, "results").ifEmpty {
                throw AiException("模型输出中未找到有效 JSON：${raw.take(200)}")
            }

        return objects.mapNotNull { obj ->
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
