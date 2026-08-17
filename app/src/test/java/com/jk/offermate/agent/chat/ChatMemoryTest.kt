package com.jk.offermate.agent.chat

import com.jk.offermate.agent.ChatMessage
import com.jk.offermate.agent.Role
import org.junit.Assert.assertEquals
import org.junit.Test

class ChatMemoryTest {

    private fun msg(i: Int) = ChatMessage(
        if (i % 2 == 0) Role.USER else Role.ASSISTANT,
        "m$i"
    )

    private fun history(n: Int) = (0 until n).map(::msg)

    // --- MessageWindowMemory ---

    @Test
    fun `message window keeps all when under limit`() {
        val h = history(3)
        assertEquals(h, MessageWindowMemory(5).apply(h))
    }

    @Test
    fun `message window keeps only the most recent N`() {
        val out = MessageWindowMemory(2).apply(history(5))
        assertEquals(listOf("m3", "m4"), out.map { it.content })
    }

    // --- TokenWindowMemory ---

    private val estimator = HeuristicTokenEstimator()

    @Test
    fun `token window trims oldest until within budget and keeps latest`() {
        val h = history(6)
        val perMsg = estimator.estimate(h.last()) // 每条等长
        val budget = perMsg * 3
        val out = TokenWindowMemory(budget, estimator).apply(h)

        assertEquals(3, out.size)
        assertEquals(listOf("m3", "m4", "m5"), out.map { it.content })
        assert(estimator.estimate(out) <= budget)
    }

    @Test
    fun `token window keeps at least the newest message even if it exceeds budget`() {
        val h = listOf(ChatMessage(Role.USER, "这是一条非常非常长的消息，用来测试单条超预算的情况啊啊啊啊啊"))
        val out = TokenWindowMemory(1, estimator).apply(h)
        assertEquals(1, out.size)
        assertEquals(h.last(), out.last())
    }

    @Test
    fun `empty history returns empty`() {
        assertEquals(emptyList<ChatMessage>(), TokenWindowMemory(100, estimator).apply(emptyList()))
    }
}
