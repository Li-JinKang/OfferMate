package com.jk.offermate.agent.pipeline

import com.jk.offermate.agent.AiException
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
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

class RelevanceMatcherTest {

    private fun loadFixture(name: String): String =
        requireNotNull(javaClass.getResourceAsStream("/fixtures/llm/$name")) {
            "缺少测试夹具: $name"
        }.bufferedReader().use { it.readText() }

    private val threeQuestions = listOf(
        ExtractedQuestion("说一下 Activity 的生命周期，onStart 和 onResume 的区别是什么？", listOf("Android", "生命周期")),
        ExtractedQuestion("Handler、Looper、MessageQueue 三者之间的关系是怎样的？", listOf("Android", "消息机制")),
        ExtractedQuestion("手写一个 LRU 缓存，要求 get 和 put 都是 O(1) 时间复杂度。", listOf("算法", "数据结构"))
    )

    @Test
    fun `match uses memory tools when enabled`() = runTest {
        val store = MemoryStore(Files.createTempDirectory("rmt").toFile())
        store.upsertProfile(MemoryProfileEntry("android", "Android", "Android 开发", "3 年"))
        val results = """{"results":[{"index":0,"score":90,"reason":"相关","matchedSkills":["Android"]}]}"""
        val llm = FakeToolCallingLlm(
            listOf(
                LlmTurn.ToolInvocations(listOf(ToolCall("c1", "list_memory_profiles", "{}"))),
                LlmTurn.Final(results)
            )
        )
        val matcher = RelevanceMatcher(
            aiClient = FakeAiClient.returning("不应走到普通补全"),
            toolCallingLlm = llm,
            toolRegistry = ToolRegistry(memoryTools(store))
        )

        val out = matcher.match(listOf(threeQuestions[0]), threshold = 60)

        assertEquals(1, out.size)
        assertEquals(90, out[0].score)
        // 首屏提示可用记忆工具，且第二轮带回 TOOL 结果
        assertTrue(llm.received.first().first.any { it.content.contains("list_memory_profiles") })
        assertTrue(llm.received[1].first.any { it.role == Role.TOOL && it.content.contains("android") })
    }

    @Test
    fun `filters by threshold and sorts by score desc`() = runTest {
        val matcher = RelevanceMatcher(FakeAiClient.returning(loadFixture("relevance_response.json")))

        val results = matcher.match(threeQuestions, threshold = 60)

        assertEquals(2, results.size)
        assertEquals(95, results[0].score)
        assertEquals(70, results[1].score)
        assertTrue(results[0].question.question.contains("Activity"))
        assertTrue(results[0].matchedSkills.contains("JVM"))
    }

    @Test
    fun `custom threshold keeps only top matches`() = runTest {
        val matcher = RelevanceMatcher(FakeAiClient.returning(loadFixture("relevance_response.json")))

        val results = matcher.match(threeQuestions, threshold = 90)

        assertEquals(1, results.size)
        assertEquals(95, results[0].score)
    }

    @Test
    fun `parse maps index back to question and ignores out-of-range`() {
        val matcher = RelevanceMatcher(FakeAiClient.returning(""))
        val raw = """{"results":[{"index":1,"score":80,"reason":"r"},{"index":9,"score":99,"reason":"越界"}]}"""

        val results = matcher.parse(raw, threeQuestions)

        assertEquals(1, results.size)
        assertTrue(results[0].question.question.contains("Handler"))
    }

    @Test
    fun `empty questions returns empty without calling model`() = runTest {
        val matcher = RelevanceMatcher(FakeAiClient.returning("should-not-be-used"))

        val results = matcher.match(emptyList())

        assertTrue(results.isEmpty())
    }

    @Test
    fun `throws AiException on malformed output`() {
        val matcher = RelevanceMatcher(FakeAiClient.returning(""))

        assertThrows(AiException::class.java) {
            matcher.parse("模型答非所问", threeQuestions)
        }
    }

    @Test
    fun `buildMessages guides memory tools and lists indexed questions`() {
        val matcher = RelevanceMatcher(FakeAiClient.returning(""))

        val messages = matcher.buildMessages(threeQuestions)

        assertTrue(messages.first().content.contains("JSON"))
        assertTrue(messages.first().content.contains("list_memory_profiles"))
        val userContent = messages.last().content
        assertTrue(userContent.contains("0. 说一下 Activity"))
        assertTrue(userContent.contains("1. Handler"))
    }
}
