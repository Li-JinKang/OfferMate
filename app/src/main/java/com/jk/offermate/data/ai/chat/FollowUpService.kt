package com.jk.offermate.data.ai.chat

import com.jk.offermate.data.ai.AiClient
import com.jk.offermate.data.ai.ChatMessage
import com.jk.offermate.data.ai.ResumeProfile

/** 一道题的追问上下文。 */
data class QuestionContext(
    val question: String,
    val currentAnswer: String,
    val tags: List<String> = emptyList()
)

/**
 * 题目追问服务：围绕**某一道题**与模型进行有状态的多轮讨论，并可据讨论产出更新后的答案。
 *
 * 与无状态的分析流水线（抽题→相关性→作答）不同，这里携带该题、当前参考答案与简历画像作为上下文，
 * 通过 [ContextAssembler] + 会话历史组装 messages。所有 Prompt 构造均为纯逻辑，可用 [com.jk.offermate.data.ai.FakeAiClient] 单测。
 */
class FollowUpService(
    private val aiClient: AiClient,
    private val assembler: ContextAssembler
) {

    /** 追问会话的 system 上下文（题目 + 当前答案 + 简历画像）。 */
    fun systemContext(context: QuestionContext, profile: ResumeProfile): String = buildString {
        append("你是一名资深面试辅导老师，正在就下面这道面试题与候选人进行**追问讨论**。\n")
        append("请结合候选人的简历画像与已有参考答案，针对其追问给出准确、有条理的解答。\n")
        append("使用 Markdown、分点作答，关键术语用 **加粗**，代码/类名用 `反引号`。\n\n")
        append("【题目】\n").append(context.question).append("\n")
        if (context.tags.isNotEmpty()) {
            append("【考点】").append(context.tags.joinToString("、")).append("\n")
        }
        if (context.currentAnswer.isNotBlank()) {
            append("\n【当前参考答案】\n").append(context.currentAnswer).append("\n")
        }
        append("\n【候选人画像】\n")
        append("目标岗位：").append(profile.targetRole.ifBlank { "未填写" }).append("\n")
        if (profile.skills.isNotEmpty()) append("技能：").append(profile.skills.joinToString("、")).append("\n")
        if (profile.projects.isNotEmpty()) append("项目：").append(profile.projects.joinToString("、")).append("\n")
    }

    /** 组装本轮要发送给模型的完整消息（history 应已包含最新的用户追问）。 */
    fun buildMessages(
        context: QuestionContext,
        profile: ResumeProfile,
        history: List<ChatMessage>
    ): List<ChatMessage> = assembler.assemble(
        systemContents = listOf(systemContext(context, profile)),
        history = history
    )

    /** 针对用户的追问生成一轮回答（history 含最新用户消息）。 */
    suspend fun reply(
        context: QuestionContext,
        profile: ResumeProfile,
        history: List<ChatMessage>
    ): String = aiClient.chat(buildMessages(context, profile, history)).trim()

    /**
     * 综合整段讨论，产出这道题**更新后的完整参考答案**（Markdown、分点，只含答案正文）。
     */
    suspend fun reviseAnswer(
        context: QuestionContext,
        profile: ResumeProfile,
        history: List<ChatMessage>
    ): String {
        val messages = assembler.assemble(
            systemContents = listOf(systemContext(context, profile), REVISE_INSTRUCTION),
            history = history
        )
        val raw = aiClient.chat(messages).trim()
        return stripCodeFence(raw)
    }

    private fun stripCodeFence(text: String): String {
        val trimmed = text.trim()
        if (!trimmed.startsWith("```")) return trimmed
        return trimmed
            .removePrefix("```markdown")
            .removePrefix("```md")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()
    }

    private companion object {
        const val REVISE_INSTRUCTION =
            "请综合以上讨论，输出这道题**更新后的完整参考答案**。要求：使用 Markdown、分点作答，" +
                "只输出答案正文，不要输出任何解释、前言或代码围栏。"
    }
}
