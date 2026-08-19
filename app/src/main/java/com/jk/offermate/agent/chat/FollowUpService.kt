package com.jk.offermate.agent.chat

import com.jk.offermate.agent.AiClient
import com.jk.offermate.agent.ChatMessage
import com.jk.offermate.agent.ResumeProfile
import com.jk.offermate.agent.ToolCallingAgent
import com.jk.offermate.agent.ToolCallingLlm
import com.jk.offermate.agent.ToolRegistry

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
 * 通过 [ContextAssembler] + 会话历史组装 messages。所有 Prompt 构造均为纯逻辑，可用 [com.jk.offermate.agent.FakeAiClient] 单测。
 */
class FollowUpService(
    private val aiClient: AiClient,
    private val assembler: ContextAssembler,
    private val toolCallingLlm: ToolCallingLlm? = null,
    private val toolRegistry: ToolRegistry = ToolRegistry(),
    private val maxSteps: Int = 5
) {

    /** 是否启用工具轮：provider 支持 function-calling 且注册了工具。 */
    private val toolsEnabled: Boolean
        get() = toolCallingLlm != null && !toolRegistry.isEmpty()

    /**
     * 对话 system 上下文。[context] 为空时是**自由对话**（不绑定题目）；非空时附带该题与当前答案。
     * 两种情况都会带上候选人画像；简历细节按需用工具拉取。
     */
    fun systemContext(context: QuestionContext?, profile: ResumeProfile): String = buildString {
        if (context == null) {
            append("你是一名资深面试辅导老师，正在与候选人进行面试相关的自由问答与讨论。\n")
            append("请结合候选人的简历画像，针对其问题给出准确、有条理的解答。\n")
            append("使用 Markdown、分点作答，关键术语用 **加粗**，代码/类名用 `反引号`。\n")
        } else {
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
        }
        append("\n【候选人画像】\n")
        append("目标岗位：").append(profile.targetRole.ifBlank { "未填写" }).append("\n")
        if (profile.skills.isNotEmpty()) append("技能：").append(profile.skills.joinToString("、")).append("\n")
        if (profile.projects.isNotEmpty()) append("项目：").append(profile.projects.joinToString("、")).append("\n")
        if (toolsEnabled) {
            append("\n如需候选人简历的更多细节（技术栈、项目经历等），调用 read_resume 工具获取，可传 query 关键词。\n")
        }
    }

    /** 组装本轮要发送给模型的完整消息（history 应已包含最新的用户输入）。 */
    fun buildMessages(
        context: QuestionContext?,
        profile: ResumeProfile,
        history: List<ChatMessage>
    ): List<ChatMessage> = assembler.assemble(
        systemContents = listOf(systemContext(context, profile)),
        history = history
    )

    /** 针对用户的输入生成一轮回答（history 含最新用户消息）。[context] 为空即自由对话。 */
    suspend fun reply(
        context: QuestionContext?,
        profile: ResumeProfile,
        history: List<ChatMessage>
    ): String = runTurn(buildMessages(context, profile, history)).trim()

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
        return stripCodeFence(runTurn(messages).trim())
    }

    /** 有工具则走 agent 工具轮（模型可按需 read_resume），否则退回普通补全。 */
    private suspend fun runTurn(messages: List<ChatMessage>): String =
        if (toolsEnabled) {
            ToolCallingAgent(toolCallingLlm!!, toolRegistry, maxSteps).run(messages)
        } else {
            aiClient.chat(messages)
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
