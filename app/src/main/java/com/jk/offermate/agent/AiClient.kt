package com.jk.offermate.agent

import com.jk.offermate.agent.tool.ToolCall

/** 对话消息角色。TOOL 表示工具执行结果消息。 */
enum class Role { SYSTEM, USER, ASSISTANT, TOOL }

/**
 * 一条对话消息。普通对话只用 [role] + [content]；工具轮额外用到：
 * - assistant 发起调用：[toolCalls] 非空；
 * - 工具返回结果：role=TOOL，[toolCallId] 指向对应调用，[content] 为结果。
 */
data class ChatMessage(
    val role: Role,
    val content: String,
    val toolCalls: List<ToolCall> = emptyList(),
    val toolCallId: String? = null,
    val toolName: String? = null
)

/**
 * 大模型调用抽象。底层可对接 DeepSeek 等 OpenAI 兼容接口（BYOK）。
 *
 * 关键设计：返回**原始文本**（assistant 回复），由上层各能力自行解析。
 * 这样抽题/相关性/作答的解析逻辑都可用 [FakeAiClient] + 录制响应在 JVM 单测中确定性验证，
 * 无需真实网络与 API Key。
 */
interface AiClient {
    suspend fun chat(messages: List<ChatMessage>): String
}

/** AI 调用/解析相关异常。 */
class AiException(message: String, cause: Throwable? = null) : Exception(message, cause)
