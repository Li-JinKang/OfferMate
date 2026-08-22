package com.jk.offermate.agent

import com.jk.offermate.data.memory.MemoryProfileEntry
import com.jk.offermate.data.memory.MemoryStore
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

class AnswerGeneratorTest {

    private fun loadFixture(name: String): String =
        requireNotNull(javaClass.getResourceAsStream("/fixtures/llm/$name")) {
            "缺少测试夹具: $name"
        }.bufferedReader().use { it.readText() }

    private val relevant = listOf(
        RelevanceResult(
            ExtractedQuestion("说一下 Activity 的生命周期，onStart 和 onResume 的区别是什么？", listOf("Android", "生命周期")),
            score = 95, reason = "高度相关", matchedSkills = listOf("Android")
        ),
        RelevanceResult(
            ExtractedQuestion("Handler、Looper、MessageQueue 三者之间的关系是怎样的？", listOf("Android", "消息机制")),
            score = 70, reason = "常考", matchedSkills = listOf("Android")
        )
    )

    @Test
    fun `answer uses memory tools when enabled`() = runTest {
        val store = MemoryStore(Files.createTempDirectory("agt").toFile())
        store.upsertProfile(MemoryProfileEntry("android", "Android", "Android 开发"))
        val answers = """{"answers":[{"index":0,"answer":"分点答案","difficulty":"medium","keyPoints":["k"]}]}"""
        val llm = FakeToolCallingLlm(
            listOf(
                LlmTurn.ToolInvocations(listOf(ToolCall("c1", "list_memory_profiles", "{}"))),
                LlmTurn.Final(answers)
            )
        )
        val generator = AnswerGenerator(
            aiClient = FakeAiClient.returning("不应走到普通补全"),
            toolCallingLlm = llm,
            toolRegistry = ToolRegistry(memoryTools(store))
        )

        val answered = generator.answer(listOf(relevant[0]))

        assertEquals(1, answered.size)
        assertEquals("分点答案", answered[0].answer)
        assertTrue(llm.received.first().first.any { it.content.contains("list_memory_profiles") })
        assertTrue(llm.received[1].first.any { it.role == Role.TOOL })
    }

    @Test
    fun `generates answers mapped back with difficulty and carried relevance`() = runTest {
        val generator = AnswerGenerator(FakeAiClient.returning(loadFixture("answer_response.json")))

        val answered = generator.answer(relevant)

        assertEquals(2, answered.size)
        assertTrue(answered[0].question.contains("Activity"))
        assertTrue(answered[0].answer.contains("onResume"))
        assertEquals(Difficulty.MEDIUM, answered[0].difficulty)
        assertTrue(answered[0].keyPoints.isNotEmpty())
        // 相关性信息应从上一步带下来
        assertEquals(95, answered[0].relevanceScore)
        assertEquals(70, answered[1].relevanceScore)
        // 标签沿用题目
        assertTrue(answered[0].tags.contains("Android"))
    }

    @Test
    fun `empty input returns empty without calling model`() = runTest {
        val generator = AnswerGenerator(FakeAiClient.returning("should-not-be-used"))

        assertTrue(generator.answer(emptyList()).isEmpty())
    }

    @Test
    fun `throws AiException on malformed output`() {
        val generator = AnswerGenerator(FakeAiClient.returning(""))

        assertThrows(AiException::class.java) {
            generator.parse("答非所问", relevant)
        }
    }
}
