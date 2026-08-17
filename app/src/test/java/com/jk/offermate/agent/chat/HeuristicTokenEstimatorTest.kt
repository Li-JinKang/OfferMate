package com.jk.offermate.agent.chat

import com.jk.offermate.agent.ChatMessage
import com.jk.offermate.agent.Role
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HeuristicTokenEstimatorTest {

    private val estimator = HeuristicTokenEstimator()

    @Test
    fun `empty text is zero tokens`() {
        assertEquals(0, estimator.estimate(""))
    }

    @Test
    fun `whitespace is ignored`() {
        assertEquals(0, estimator.estimate("   \n\t "))
    }

    @Test
    fun `cjk counted around 1_5 chars per token`() {
        // 3 个汉字 → ceil(3 / 1.5) = 2
        assertEquals(2, estimator.estimate("你好呀"))
    }

    @Test
    fun `ascii counted around 4 chars per token`() {
        // 8 个非空白字符 → ceil(8 / 4) = 2
        assertEquals(2, estimator.estimate("abcdefgh"))
    }

    @Test
    fun `message includes fixed overhead`() {
        val msg = ChatMessage(Role.USER, "你好呀") // 2 tokens + overhead
        assertEquals(2 + TokenEstimator.MESSAGE_OVERHEAD, estimator.estimate(msg))
    }

    @Test
    fun `longer text yields more tokens`() {
        val short = estimator.estimate("Kotlin 协程")
        val long = estimator.estimate("Kotlin 协程的结构化并发与取消传播机制原理")
        assertTrue(long > short)
    }
}
