package com.jk.offermate.agent.tool

import com.jk.offermate.agent.AiClient
import com.jk.offermate.agent.ChatMessage

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

/**
 * 支持**流式**输出的模型端口。与 [ToolCallingLlm.chat] 一轮的语义一致，但最终文本会在生成过程中
 * 通过 [onDelta] 增量回调（增量为“新增的文本片段”）。
 *
 * 约定：若这一轮模型决定调用工具（返回 [LlmTurn.ToolInvocations]），则**不应**回调任何文本增量；
 * 只有产出最终文本（[LlmTurn.Final]）时才通过 [onDelta] 流式回调。这样上层 agent 可以：工具轮静默执行，
 * 仅最终答案对用户流式呈现。
 */
interface StreamingLlm {
    suspend fun chatStream(
        messages: List<ChatMessage>,
        tools: List<ToolSpec>,
        onDelta: (String) -> Unit
    ): LlmTurn
}
