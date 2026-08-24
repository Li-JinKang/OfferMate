package com.jk.offermate.agent.chat

import com.jk.offermate.agent.ChatMessage
import com.jk.offermate.agent.FakeAiClient
import com.jk.offermate.agent.Role
import com.jk.offermate.agent.tool.FakeToolCallingLlm
import com.jk.offermate.agent.tool.LlmTurn
import com.jk.offermate.agent.tool.ToolCall
import com.jk.offermate.agent.tool.ToolRegistry
import com.jk.offermate.agent.tool.memoryTools
import com.jk.offermate.data.memory.MemoryProfileEntry
import com.jk.offermate.data.memory.MemoryStore
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FollowUpServiceTest {

    private val context = QuestionContext(
        question = "谈谈 Kotlin 协程的结构化并发",
        currentAnswer = "结构化并发通过作用域管理协程生命周期。",
        tags = listOf("Kotlin", "并发")
    )

    private fun service(fake: FakeAiClient) =
        FollowUpService(fake, ContextAssembler(MessageWindowMemory(20)))

    @Test
    fun `reply uses memory tools when tool calling enabled`() = runTest {
        val store = MemoryStore(java.nio.file.Files.createTempDirectory("fus").toFile())
        store.upsertProfile(MemoryProfileEntry("android", "Android", "Android 开发"))
        val llm = FakeToolCallingLlm(
            listOf(
                LlmTurn.ToolInvocations(listOf(ToolCall("c1", "list_memory_profiles", "{}"))),
                LlmTurn.Final("结合简历后的回答")
            )
        )
        val service = FollowUpService(
            aiClient = FakeAiClient.returning("不应走到普通补全"),
            assembler = ContextAssembler(MessageWindowMemory(20)),
            toolCallingLlm = llm,
            toolRegistry = ToolRegistry(memoryTools(store))
        )

        val out = service.reply(context, listOf(ChatMessage(Role.USER, "结合我的经历怎么答？")))

        assertEquals("结合简历后的回答", out)
        // system 提示可用记忆工具
        assertTrue(llm.received.first().first.first().content.contains("list_memory_profiles"))
        // 第二轮带回 TOOL 结果（记忆内容）
        assertTrue(llm.received[1].first.any { it.role == Role.TOOL && it.content.contains("android") })
    }

    @Test
    fun `reply injects question and current answer into system context`() = runTest {
        val fake = FakeAiClient.returning("这是回答")
        val history = listOf(ChatMessage(Role.USER, "能举个取消传播的例子吗？"))

        val out = service(fake).reply(context, history)

        assertEquals("这是回答", out)
        val sent = fake.lastMessages
        val system = sent.first()
        assertEquals(Role.SYSTEM, system.role)
        assertTrue(system.content.contains("谈谈 Kotlin 协程的结构化并发"))
        assertTrue(system.content.contains("结构化并发通过作用域管理协程生命周期"))
        // 历史追问被带上，且在 system 之后
        assertEquals("能举个取消传播的例子吗？", sent.last().content)
    }

    @Test
    fun `reply trims model output`() = runTest {
        val fake = FakeAiClient.returning("  有前后空白  ")
        val out = service(fake).reply(context, emptyList())
        assertEquals("有前后空白", out)
    }

    @Test
    fun `reviseAnswer adds revise instruction and strips code fence`() = runTest {
        val fake = FakeAiClient.returning("```markdown\n1. 更新后的要点\n2. 第二点\n```")
        val history = listOf(
            ChatMessage(Role.USER, "请补充线程调度细节"),
            ChatMessage(Role.ASSISTANT, "……")
        )

        val revised = service(fake).reviseAnswer(context, history)

        assertEquals("1. 更新后的要点\n2. 第二点", revised)
        val sent = fake.lastMessages
        val systemJoined = sent.filter { it.role == Role.SYSTEM }.joinToString("\n") { it.content }
        assertTrue(systemJoined.contains("更新后的完整参考答案"))
        assertFalse(revised.contains("```"))
    }

    @Test
    fun `buildMessages places system first and history after`() {
        val fake = FakeAiClient.returning("x")
        val history = listOf(ChatMessage(Role.USER, "问题A"), ChatMessage(Role.ASSISTANT, "回答A"))
        val msgs = service(fake).buildMessages(context, history)

        assertEquals(Role.SYSTEM, msgs.first().role)
        assertEquals(listOf("问题A", "回答A"), msgs.drop(1).map { it.content })
    }
}
