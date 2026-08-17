package com.jk.offermate.agent.chat

import com.jk.offermate.agent.ChatMessage
import kotlin.math.ceil

/**
 * Token 估算器抽象。端侧没有 DeepSeek 的真实分词器，只用启发式估算做**预算控制**，
 * 不追求精确。抽象成接口便于替换与在单测中固定行为。
 */
interface TokenEstimator {
    /** 估算一段文本的 token 数。 */
    fun estimate(text: String): Int

    /** 估算一条消息的 token 数（含角色/分隔的小额固定开销）。 */
    fun estimate(message: ChatMessage): Int = estimate(message.content) + MESSAGE_OVERHEAD

    /** 估算一组消息的总 token 数。 */
    fun estimate(messages: List<ChatMessage>): Int = messages.sumOf { estimate(it) }

    companion object {
        /** 每条消息的固定开销（角色标记等），近似值。 */
        const val MESSAGE_OVERHEAD = 4
    }
}

/**
 * 启发式 Token 估算：
 * - CJK（中日韩）字符约 1.5 字符/token；
 * - 其余可见字符约 4 字符/token；
 * - 空白字符忽略。
 */
class HeuristicTokenEstimator(
    private val cjkCharsPerToken: Double = 1.5,
    private val otherCharsPerToken: Double = 4.0
) : TokenEstimator {

    override fun estimate(text: String): Int {
        if (text.isEmpty()) return 0
        var cjk = 0
        var other = 0
        for (c in text) {
            when {
                c.isWhitespace() -> Unit
                isCjk(c) -> cjk++
                else -> other++
            }
        }
        val tokens = cjk / cjkCharsPerToken + other / otherCharsPerToken
        return ceil(tokens).toInt()
    }

    private fun isCjk(c: Char): Boolean {
        val block = Character.UnicodeBlock.of(c)
        return block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS ||
            block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A ||
            block == Character.UnicodeBlock.CJK_SYMBOLS_AND_PUNCTUATION ||
            block == Character.UnicodeBlock.HALFWIDTH_AND_FULLWIDTH_FORMS ||
            block == Character.UnicodeBlock.HIRAGANA ||
            block == Character.UnicodeBlock.KATAKANA ||
            block == Character.UnicodeBlock.HANGUL_SYLLABLES
    }
}
