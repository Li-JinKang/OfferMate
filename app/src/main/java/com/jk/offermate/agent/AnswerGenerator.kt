package com.jk.offermate.agent

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

/**
 * AI 分析流水线第三步：为筛选出的相关题目生成参考答案。
 */
class AnswerGenerator(
    private val aiClient: AiClient,
    private val toolCallingLlm: ToolCallingLlm? = null,
    private val toolRegistry: ToolRegistry = ToolRegistry(),
    private val maxSteps: Int = 5
) {

    private val toolsEnabled: Boolean
        get() = toolCallingLlm != null && !toolRegistry.isEmpty()

    fun buildMessages(relevant: List<RelevanceResult>): List<ChatMessage> {
        val questionsBlock = relevant
            .mapIndexed { index, r -> "$index. ${r.question.question}" }
            .joinToString("\n")

        return listOf(
            ChatMessage(
                role = Role.SYSTEM,
                content = """
                    你是一名资深面试辅导老师。请为每道题给出**参考答案**、难度评级与关键要点，
                    并尽量结合候选人的真实项目经历给出作答建议。
                    获取候选人背景的方式（按需分级调用工具，不要臆造）：
                    1. 先 list_memory_profiles 查看有哪些求职方向记忆，选择与题目最相关的一份。
                    2. load_profile_overview(profileId) 加载该方向的技能与项目/经历概览。
                    3. 需要具体项目/经历时再 load_project_detail / load_experience_detail 下钻。
                    严格要求：
                    1. 最终仅输出 JSON，不要输出任何解释或额外文字。
                    2. 结构为：{"answers":[{"index":题目序号,"answer":"参考答案","difficulty":"easy|medium|hard","keyPoints":["要点"]}]}。
                    3. index 必须与输入题目的序号一一对应。
                    4. answer 字段使用 **Markdown** 且**分点作答**：用有序列表(1. 2. 3.)或无序列表(- )组织要点，
                       关键术语用 **加粗**，代码/类名用 `反引号`。保持条理清晰、简明扼要。
                    5. 整体必须是**合法 JSON**：answer 内的换行一律用 \n 转义，字符串内的引号用 \" 转义，
                       **不要在 JSON 中使用 ``` 代码围栏**（会破坏 JSON）；多行代码写在同一字符串里并用 \n 分隔。
                """.trimIndent()
            ),
            ChatMessage(
                role = Role.USER,
                content = buildString {
                    append("待作答题目：\n")
                    append(questionsBlock)
                }
            )
        )
    }

    suspend fun answer(relevant: List<RelevanceResult>): List<AnsweredQuestion> {
        if (relevant.isEmpty()) return emptyList()
        val raw = runTurn(buildMessages(relevant))
        return parse(raw, relevant)
    }

    /** 有工具则走 agent 工具轮（模型可按需调用记忆工具），否则退回普通补全。 */
    private suspend fun runTurn(messages: List<ChatMessage>): String =
        if (toolsEnabled) {
            ToolCallingAgent(toolCallingLlm!!, toolRegistry, maxSteps).run(messages)
        } else {
            aiClient.chat(messages)
        }

    fun parse(raw: String, relevant: List<RelevanceResult>): List<AnsweredQuestion> {
        // 正常路径：截出完整 JSON 并解析出 answers 数组。
        val parsedArray: JsonArray? = JsonSupport.extractJsonBlock(raw)
            ?.let { runCatching { JsonSupport.json.parseToJsonElement(it) }.getOrNull() }
            ?.let { element ->
                when {
                    element is JsonArray -> element
                    element is JsonObject && element["answers"] is JsonArray -> element["answers"]!!.jsonArray
                    else -> null
                }
            }

        // 抢救路径：整体无法解析（多为输出超长被截断）时，尽量捞回已写完的答案对象，
        // 避免"最后一题没写完 → 整批全丢"。
        val objects: List<JsonObject> = parsedArray?.filterIsInstance<JsonObject>()
            ?: JsonSupport.salvageObjects(raw)

        if (objects.isEmpty()) {
            throw AiException("模型输出中未找到有效答案 JSON：${raw.take(200)}")
        }

        return objects.mapNotNull { obj ->
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
