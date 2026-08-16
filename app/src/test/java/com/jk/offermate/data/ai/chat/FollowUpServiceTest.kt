package com.jk.offermate.data.ai.chat

import com.jk.offermate.data.ai.ChatMessage
import com.jk.offermate.data.ai.FakeAiClient
import com.jk.offermate.data.ai.ResumeProfile
import com.jk.offermate.data.ai.Role
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FollowUpServiceTest {

    private val profile = ResumeProfile(
        targetRole = "Android 开发",
        skills = listOf("Kotlin", "Compose"),
        projects = listOf("即时通讯 App")
    )
    private val context = QuestionContext(
        question = "谈谈 Kotlin 协程的结构化并发",
        currentAnswer = "结构化并发通过作用域管理协程生命周期。",
        tags = listOf("Kotlin", "并发")
    )

    private fun service(fake: FakeAiClient) =
        FollowUpService(fake, ContextAssembler(MessageWindowMemory(20)))

    @Test
    fun `reply injects question current answer and profile into system context`() = runTest {
        val fake = FakeAiClient.returning("这是回答")
        val history = listOf(ChatMessage(Role.USER, "能举个取消传播的例子吗？"))

        val out = service(fake).reply(context, profile, history)

        assertEquals("这是回答", out)
        val sent = fake.lastMessages
        val system = sent.first()
        assertEquals(Role.SYSTEM, system.role)
        assertTrue(system.content.contains("谈谈 Kotlin 协程的结构化并发"))
        assertTrue(system.content.contains("结构化并发通过作用域管理协程生命周期"))
        assertTrue(system.content.contains("Android 开发"))
        assertTrue(system.content.contains("Kotlin"))
        // 历史追问被带上，且在 system 之后
        assertEquals("能举个取消传播的例子吗？", sent.last().content)
    }

    @Test
    fun `reply trims model output`() = runTest {
        val fake = FakeAiClient.returning("  有前后空白  ")
        val out = service(fake).reply(context, profile, emptyList())
        assertEquals("有前后空白", out)
    }

    @Test
    fun `reviseAnswer adds revise instruction and strips code fence`() = runTest {
        val fake = FakeAiClient.returning("```markdown\n1. 更新后的要点\n2. 第二点\n```")
        val history = listOf(
            ChatMessage(Role.USER, "请补充线程调度细节"),
            ChatMessage(Role.ASSISTANT, "……")
        )

        val revised = service(fake).reviseAnswer(context, profile, history)

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
        val msgs = service(fake).buildMessages(context, profile, history)

        assertEquals(Role.SYSTEM, msgs.first().role)
        assertEquals(listOf("问题A", "回答A"), msgs.drop(1).map { it.content })
    }
}
