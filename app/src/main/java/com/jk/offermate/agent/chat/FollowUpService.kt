package com.jk.offermate.agent.chat

import com.jk.offermate.agent.AiClient
import com.jk.offermate.agent.ChatMessage
import com.jk.offermate.agent.Role
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
    fun systemContext(context: QuestionContext?): String = buildString {
        if (context == null) {
            append("你是一名资深面试辅导老师，正在与候选人进行面试相关的自由问答与讨论。\n")
            append("请针对其问题给出准确、有条理的解答。\n")
            append("使用 Markdown、分点作答，关键术语用 **加粗**，代码/类名用 `反引号`。\n")
        } else {
            append("你是一名资深面试辅导老师，正在就下面这道面试题与候选人进行**追问讨论**。\n")
            append("请结合已有参考答案，针对其追问给出准确、有条理的解答。\n")
            append("使用 Markdown、分点作答，关键术语用 **加粗**，代码/类名用 `反引号`。\n\n")
            append("【题目】\n").append(context.question).append("\n")
            if (context.tags.isNotEmpty()) {
                append("【考点】").append(context.tags.joinToString("、")).append("\n")
            }
            if (context.currentAnswer.isNotBlank()) {
                append("\n【当前参考答案】\n").append(context.currentAnswer).append("\n")
            }
        }
        if (toolsEnabled) {
            append("\n如需结合候选人的简历背景（求职方向、技能、项目经历）作答，按需分级调用工具：\n")
            append("先 list_memory_profiles 查看有哪些方向记忆并选相关的一份，")
            append("再 load_profile_overview 看概览，必要时 load_project_detail / load_experience_detail 下钻。\n")
        }
    }

    /** 组装本轮要发送给模型的完整消息（history 应已包含最新的用户输入）。 */
    fun buildMessages(
        context: QuestionContext?,
        history: List<ChatMessage>
    ): List<ChatMessage> = assembler.assemble(
        systemContents = listOf(systemContext(context)),
        history = history
    )

    /** 针对用户的输入生成一轮回答（history 含最新用户消息）。[context] 为空即自由对话。 */
    suspend fun reply(
        context: QuestionContext?,
        history: List<ChatMessage>
    ): String = runTurn(buildMessages(context, history)).trim()

    /**
     * 综合整段讨论，产出这道题**更新后的完整参考答案**（Markdown、分点，只含答案正文）。
     */
    suspend fun reviseAnswer(
        context: QuestionContext,
        history: List<ChatMessage>
    ): String {
        val messages = assembler.assemble(
            systemContents = listOf(systemContext(context), REVISE_INSTRUCTION),
            history = history
        )
        return stripCodeFence(runTurn(messages).trim())
    }

    /**
     * 根据首轮对话（用户提问 + 模型回答）生成一个简短标题，用作会话标题。
     * 直接走普通补全（不走工具轮），失败由调用方兜底。
     */
    suspend fun summarizeTitle(userMessage: String, assistantReply: String): String {
        val messages = listOf(
            ChatMessage(role = Role.SYSTEM, content = TITLE_INSTRUCTION),
            ChatMessage(
                role = Role.USER,
                content = "用户提问：${userMessage.take(500)}\n\n回答：${assistantReply.take(500)}"
            )
        )
        return sanitizeTitle(aiClient.chat(messages))
    }

    /** 清洗模型返回的标题：取首行、去除引号/书名号/末尾标点，限制长度。 */
    private fun sanitizeTitle(raw: String): String =
        raw.trim()
            .lineSequence()
            .firstOrNull { it.isNotBlank() }
            .orEmpty()
            .trim()
            .trim('"', '\'', '「', '」', '“', '”', '《', '》', '.', '。', '：', ':')
            .take(15)

    /** 有工具则走 agent 工具轮（模型可按需调用记忆工具），否则退回普通补全。 */
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
        const val TITLE_INSTRUCTION =
            "请用一句不超过15个字的简短标题概括这轮对话的主题。只输出标题本身，" +
                "不要引号、标点符号、前后缀或任何解释。"

        const val REVISE_INSTRUCTION =
            "请综合以上讨论，输出这道题**更新后的完整参考答案**。要求：使用 Markdown、分点作答，" +
                "只输出答案正文，不要输出任何解释、前言或代码围栏。"
    }
}
