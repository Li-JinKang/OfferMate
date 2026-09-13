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

/**
 * AI 调用/解析相关异常。
 *
 * [retryable] 是整条导入链路的重试契约：网络抖动、限流、5xx 为 true，任务层会走 `Result.retry()`；
 * Key 无效、余额不足、模型输出不可解析为 false，重试只会白烧 token。分类矩阵见
 * docs/plan/network-resilience.md 第 4 节。
 */
class AiException(
    message: String,
    val retryable: Boolean = false,
    cause: Throwable? = null
) : Exception(message, cause)
