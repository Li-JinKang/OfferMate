package com.jk.offermate.data.ai.chat

import com.jk.offermate.data.ai.ChatMessage
import com.jk.offermate.data.ai.Role
import org.junit.Assert.assertEquals
import org.junit.Test

class ContextAssemblerTest {

    private val assembler = ContextAssembler(MessageWindowMemory(10))

    @Test
    fun `assembles system then history then current input in order`() {
        val history = listOf(
            ChatMessage(Role.USER, "u1"),
            ChatMessage(Role.ASSISTANT, "a1")
        )
        val out = assembler.assemble(
            systemContents = listOf("role", "memory"),
            history = history,
            currentInput = "现在的问题"
        )

        assertEquals(Role.SYSTEM, out[0].role)
        assertEquals("role", out[0].content)
        assertEquals(Role.SYSTEM, out[1].role)
        assertEquals("memory", out[1].content)
        assertEquals("u1", out[2].content)
        assertEquals("a1", out[3].content)
        assertEquals(Role.USER, out[4].role)
        assertEquals("现在的问题", out[4].content)
    }

    @Test
    fun `blank system contents are skipped`() {
        val out = assembler.assemble(
            systemContents = listOf("role", "  ", ""),
            history = emptyList(),
            currentInput = null
        )
        assertEquals(1, out.size)
        assertEquals("role", out[0].content)
    }

    @Test
    fun `no current input when null or blank`() {
        val out = assembler.assemble(
            systemContents = listOf("role"),
            history = listOf(ChatMessage(Role.USER, "u1")),
            currentInput = "   "
        )
        assertEquals(2, out.size)
        assertEquals(Role.USER, out.last().role)
        assertEquals("u1", out.last().content)
    }

    @Test
    fun `memory strategy is applied to history`() {
        val small = ContextAssembler(MessageWindowMemory(1))
        val out = small.assemble(
            systemContents = listOf("role"),
            history = listOf(ChatMessage(Role.USER, "old"), ChatMessage(Role.ASSISTANT, "new")),
            currentInput = null
        )
        // system + 仅保留最近 1 条历史
        assertEquals(2, out.size)
        assertEquals("new", out.last().content)
    }
}
