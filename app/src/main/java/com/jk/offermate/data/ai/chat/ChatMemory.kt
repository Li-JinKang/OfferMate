package com.jk.offermate.data.ai.chat

import com.jk.offermate.data.ai.ChatMessage

/**
 * 会话记忆策略：给定完整的历史消息（user/assistant，不含 system），
 * 产出**要实际发送给模型**的历史窗口（可能被裁剪）。始终保留最新的消息。
 *
 * system 消息与摘要由 [ContextAssembler] 负责拼装，这里只处理对话历史窗口。
 */
interface ChatMemory {
    fun apply(history: List<ChatMessage>): List<ChatMessage>
}

/** 保留最近 [maxMessages] 条消息。 */
class MessageWindowMemory(private val maxMessages: Int) : ChatMemory {
    init {
        require(maxMessages > 0) { "maxMessages 必须为正" }
    }

    override fun apply(history: List<ChatMessage>): List<ChatMessage> =
        if (history.size <= maxMessages) history
        else history.subList(history.size - maxMessages, history.size)
}

/**
 * 按 token 预算保留最近若干条消息：从最新往回累加，直到超出 [maxTokens] 为止。
 * 若单条消息已超预算，则至少保留最新的一条（避免返回空历史）。
 */
class TokenWindowMemory(
    private val maxTokens: Int,
    private val estimator: TokenEstimator
) : ChatMemory {
    init {
        require(maxTokens > 0) { "maxTokens 必须为正" }
    }

    override fun apply(history: List<ChatMessage>): List<ChatMessage> {
        if (history.isEmpty()) return history
        var used = 0
        var startIndex = history.size
        for (i in history.indices.reversed()) {
            val cost = estimator.estimate(history[i])
            if (used + cost > maxTokens && i != history.size - 1) break
            used += cost
            startIndex = i
        }
        return history.subList(startIndex, history.size)
    }
}
