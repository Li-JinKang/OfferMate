package com.jk.offermate.data.ai.chat

import com.jk.offermate.data.ai.ChatMessage
import com.jk.offermate.data.ai.Role

/**
 * 上下文组装：把要发送给模型的 `messages` 按固定顺序拼装：
 *
 * ```
 * [system: 角色与任务约束 / 激活档案上下文 / 长期摘要 …（按传入顺序）]
 * [ …经记忆策略裁剪后的历史 user/assistant 消息… ]
 * [user: 当前输入（若提供）]
 * ```
 *
 * 纯逻辑、无副作用，便于单测断言顺序与角色。
 */
class ContextAssembler(private val memory: ChatMemory) {

    /**
     * @param systemContents 依次拼在最前面的 system 段（空白项会被忽略）。
     * @param history        历史对话（user/assistant），交给记忆策略裁剪。
     * @param currentInput   本轮用户输入；为 null/空白时不追加（history 已含最新输入的场景）。
     */
    fun assemble(
        systemContents: List<String>,
        history: List<ChatMessage>,
        currentInput: String? = null
    ): List<ChatMessage> {
        val out = ArrayList<ChatMessage>()
        systemContents
            .filter { it.isNotBlank() }
            .forEach { out += ChatMessage(Role.SYSTEM, it) }
        out += memory.apply(history)
        if (!currentInput.isNullOrBlank()) {
            out += ChatMessage(Role.USER, currentInput)
        }
        return out
    }
}
