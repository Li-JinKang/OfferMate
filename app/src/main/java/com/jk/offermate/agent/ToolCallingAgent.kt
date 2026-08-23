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
    private val maxSteps: Int = 15,
    private val logger: AgentLogger = NoopAgentLogger
) {
    suspend fun run(initialMessages: List<ChatMessage>): String {
        val conversation = initialMessages.toMutableList()
        logger.d { "agent.run 开始：消息=${conversation.size}，可用工具=${tools.specs().joinToString { it.name }}" }

        repeat(maxSteps) { step ->
            when (val turn = llm.chat(conversation, tools.specs())) {
                is LlmTurn.Final -> {
                    logger.d { "agent.run 第${step + 1}步 → 最终文本(len=${turn.text.length})" }
                    return turn.text
                }
                is LlmTurn.ToolInvocations -> {
                    logger.d { "agent.run 第${step + 1}步 → 请求工具：${turn.calls.joinToString { it.name }}" }
                    executeCalls(conversation, turn.calls)
                }
            }
        }

        // 超出步数上限：不再提供工具，逼模型给最终答复
        logger.w { "agent.run 达到步数上限 maxSteps=$maxSteps，改为不带工具强制收敛" }
        return when (val turn = llm.chat(conversation, emptyList())) {
            is LlmTurn.Final -> turn.text
            // 极端情况下模型仍执意调用工具：返回友好兜底，绝不把裸标记透出给用户。
            is LlmTurn.ToolInvocations -> STEP_LIMIT_FALLBACK
        }
    }

    /**
     * 流式执行：工具轮静默执行（不流式），仅**最终文本**通过 [onDelta] 增量回调。
     * 若底层模型不支持流式（非 [StreamingLlm]），自动退回非流式 [run]。
     */
    suspend fun runStreaming(initialMessages: List<ChatMessage>, onDelta: (String) -> Unit): String {
        val streaming = llm as? StreamingLlm ?: return run(initialMessages)
        val conversation = initialMessages.toMutableList()
        logger.d { "agent.stream 开始：消息=${conversation.size}，可用工具=${tools.specs().joinToString { it.name }}" }

        repeat(maxSteps) { step ->
            when (val turn = streaming.chatStream(conversation, tools.specs(), onDelta)) {
                is LlmTurn.Final -> {
                    logger.d { "agent.stream 第${step + 1}步 → 最终文本(len=${turn.text.length})" }
                    return turn.text
                }
                is LlmTurn.ToolInvocations -> {
                    logger.d { "agent.stream 第${step + 1}步 → 请求工具：${turn.calls.joinToString { it.name }}" }
                    executeCalls(conversation, turn.calls)
                }
            }
        }

        logger.w { "agent.stream 达到步数上限 maxSteps=$maxSteps，改为不带工具强制收敛" }
        return when (val turn = streaming.chatStream(conversation, emptyList(), onDelta)) {
            is LlmTurn.Final -> turn.text
            is LlmTurn.ToolInvocations -> STEP_LIMIT_FALLBACK
        }
    }

    /** 记录 assistant 的工具调用，逐个本地执行并把结果作为 TOOL 消息回填。 */
    private suspend fun executeCalls(conversation: MutableList<ChatMessage>, calls: List<ToolCall>) {
        conversation += ChatMessage(Role.ASSISTANT, "", toolCalls = calls)
        calls.forEach { call ->
            logger.d { "工具调用 → ${call.name} 参数=${AgentLogger.brief(call.argumentsJson)}" }
            val started = System.currentTimeMillis()
            val tool = tools.find(call.name)
            val result = if (tool == null) {
                logger.w { "工具未找到：${call.name}" }
                "未知工具：${call.name}"
            } else {
                runCatching { tool.call(call.argumentsJson) }
                    .onSuccess { r ->
                        logger.d {
                            "工具返回 ← ${call.name} 耗时=${System.currentTimeMillis() - started}ms " +
                                "结果=${AgentLogger.brief(r)}"
                        }
                    }
                    .getOrElse { e ->
                        logger.w(e) { "工具执行失败：${call.name}" }
                        "工具执行失败：${e.message}"
                    }
            }
            conversation += ChatMessage(
                role = Role.TOOL,
                content = result,
                toolCallId = call.id,
                toolName = call.name
            )
        }
    }

    private companion object {
        const val STEP_LIMIT_FALLBACK = "抱歉，我这次没能整理出完整回答，请换个说法再问一次。"
    }
}
