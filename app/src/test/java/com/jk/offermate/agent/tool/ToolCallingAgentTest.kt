package com.jk.offermate.agent.tool

import com.jk.offermate.agent.ChatMessage
import com.jk.offermate.agent.Role
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolCallingAgentTest {

    private class RecordingTool(name: String, private val result: String) : Tool {
        override val spec = ToolSpec(name, "desc")
        var lastArgs: String? = null
        override suspend fun call(argumentsJson: String): String {
            lastArgs = argumentsJson
            return result
        }
    }

    @Test
    fun `executes tool call then returns final answer`() = runTest {
        val tool = RecordingTool("read_resume", "简历：熟悉 Kotlin/Android")
        val llm = FakeToolCallingLlm(
            listOf(
                LlmTurn.ToolInvocations(listOf(ToolCall("c1", "read_resume", """{"query":"kotlin"}"""))),
                LlmTurn.Final("综合简历后的最终答案")
            )
        )
        val agent = ToolCallingAgent(llm, ToolRegistry(listOf(tool)))

        val out = agent.run(listOf(ChatMessage(Role.USER, "这道题结合我的经历怎么答？")))

        assertEquals("综合简历后的最终答案", out)
        assertEquals("""{"query":"kotlin"}""", tool.lastArgs)

        // 第二轮请求里应带上 assistant 的 tool_calls 与 TOOL 结果
        val secondRequest = llm.received[1].first
        assertTrue(secondRequest.any { it.role == Role.ASSISTANT && it.toolCalls.isNotEmpty() })
        assertTrue(secondRequest.any { it.role == Role.TOOL && it.content.contains("熟悉 Kotlin") })
    }

    @Test
    fun `unknown tool is reported back and loop continues`() = runTest {
        val llm = FakeToolCallingLlm(
            listOf(
                LlmTurn.ToolInvocations(listOf(ToolCall("c1", "nope", "{}"))),
                LlmTurn.Final("兜底答案")
            )
        )
        val agent = ToolCallingAgent(llm, ToolRegistry(emptyList()))

        val out = agent.run(listOf(ChatMessage(Role.USER, "q")))

        assertEquals("兜底答案", out)
        val toolMsg = llm.received[1].first.first { it.role == Role.TOOL }
        assertTrue(toolMsg.content.contains("未知工具"))
    }

    @Test
    fun `stops at max steps`() = runTest {
        // 模型一直要求调用工具，永不给最终答复
        val alwaysTool = LlmTurn.ToolInvocations(listOf(ToolCall("c", "read_resume", "{}")))
        val llm = FakeToolCallingLlm(listOf(alwaysTool))
        val agent = ToolCallingAgent(llm, ToolRegistry(listOf(RecordingTool("read_resume", "x"))), maxSteps = 2)

        val out = agent.run(listOf(ChatMessage(Role.USER, "q")))

        // 超出步数上限且模型仍执意调用工具：返回友好兜底文案（而非空串或裸标记）
        assertTrue(out.isNotBlank())
        assertTrue(!out.contains("<"))
        // maxSteps 次循环 + 1 次不带工具的兜底调用
        assertEquals(3, llm.received.size)
        assertTrue(llm.received.last().second.isEmpty()) // 兜底调用不带工具
    }
}
