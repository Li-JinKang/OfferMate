package com.jk.offermate.agent

/**
 * 工具调用循环（agent 核心）：
 * 发送 messages + 可用工具 → 若模型要求调用工具，则本地执行并把结果回填 → 再次发送，
 * 直到模型给出最终文本或达到 [maxSteps]。
 *
 * 纯编排逻辑，用 fake [ToolCallingLlm] + [Tool] 即可 JVM 单测。
 */
class ToolCallingAgent(
    private val llm: ToolCallingLlm,
    private val tools: ToolRegistry,
    private val maxSteps: Int = 5
) {
    suspend fun run(initialMessages: List<ChatMessage>): String {
        val conversation = initialMessages.toMutableList()

        repeat(maxSteps) {
            when (val turn = llm.chat(conversation, tools.specs())) {
                is LlmTurn.Final -> return turn.text
                is LlmTurn.ToolInvocations -> {
                    // 记录 assistant 的工具调用，再逐个执行并回填结果
                    conversation += ChatMessage(Role.ASSISTANT, "", toolCalls = turn.calls)
                    turn.calls.forEach { call ->
                        val result = tools.find(call.name)
                            ?.let { tool ->
                                runCatching { tool.call(call.argumentsJson) }
                                    .getOrElse { e -> "工具执行失败：${e.message}" }
                            }
                            ?: "未知工具：${call.name}"
                        conversation += ChatMessage(
                            role = Role.TOOL,
                            content = result,
                            toolCallId = call.id,
                            toolName = call.name
                        )
                    }
                }
            }
        }

        // 超出步数上限：不再提供工具，逼模型给最终答复
        return when (val turn = llm.chat(conversation, emptyList())) {
            is LlmTurn.Final -> turn.text
            is LlmTurn.ToolInvocations -> ""
        }
    }
}
