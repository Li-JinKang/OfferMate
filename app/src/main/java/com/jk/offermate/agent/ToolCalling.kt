package com.jk.offermate.agent

/** 一次工具调用请求（由模型发起）。 */
data class ToolCall(
    val id: String,
    val name: String,
    /** 参数（JSON 字符串，OpenAI function.arguments 约定）。 */
    val argumentsJson: String
)

/** 工具的对外描述（提供给模型的 function schema）。 */
data class ToolSpec(
    val name: String,
    val description: String,
    /** 参数的 JSON Schema（JSON 字符串）；为空表示无参数。 */
    val parametersJson: String = ""
)

/** 模型一轮的产出：要么是最终文本，要么是若干工具调用。 */
sealed interface LlmTurn {
    data class Final(val text: String) : LlmTurn
    data class ToolInvocations(val calls: List<ToolCall>) : LlmTurn
}

/**
 * 支持工具调用的模型端口（Strategy）。与 [AiClient] 的简单补全分开，
 * 只有需要 function-calling 的场景才用；纯逻辑（agent 循环）可用 fake 实现单测。
 */
interface ToolCallingLlm {
    suspend fun chat(messages: List<ChatMessage>, tools: List<ToolSpec>): LlmTurn
}
