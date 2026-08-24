package com.jk.offermate.agent.tool

import com.jk.offermate.agent.ChatMessage

/** 测试用的 ToolCallingLlm：按序返回预置轮次，并记录每次收到的消息与工具。 */
class FakeToolCallingLlm(private val turns: List<LlmTurn>) : ToolCallingLlm {

    val received = mutableListOf<Pair<List<ChatMessage>, List<ToolSpec>>>()

    override suspend fun chat(messages: List<ChatMessage>, tools: List<ToolSpec>): LlmTurn {
        received += messages.toList() to tools
        val index = (received.size - 1).coerceAtMost(turns.lastIndex)
        return turns[index]
    }
}
