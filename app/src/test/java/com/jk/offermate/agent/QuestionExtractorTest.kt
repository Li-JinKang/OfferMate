package com.jk.offermate.agent

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class QuestionExtractorTest {

    private fun loadFixture(name: String): String =
        requireNotNull(javaClass.getResourceAsStream("/fixtures/llm/$name")) {
            "缺少测试夹具: $name"
        }.bufferedReader().use { it.readText() }

    @Test
    fun `parses questions from recorded llm response`() {
        val extractor = QuestionExtractor(FakeAiClient.returning(""))

        val questions = extractor.parse(loadFixture("extract_response.json"))

        assertEquals(3, questions.size)
        assertEquals("说一下 Activity 的生命周期，onStart 和 onResume 的区别是什么？", questions[0].question)
        assertTrue(questions[0].tags.contains("Android"))
        assertEquals("算法", questions[2].tags.first())
    }

    @Test
    fun `strips markdown json fence and surrounding text`() {
        val extractor = QuestionExtractor(FakeAiClient.returning(""))
        val raw = """
            好的，以下是抽取结果：
            ```json
            {"questions":[{"question":"什么是协程的挂起？","tags":["Kotlin"]}]}
            ```
            希望有帮助。
        """.trimIndent()

        val questions = extractor.parse(raw)

        assertEquals(1, questions.size)
        assertEquals("什么是协程的挂起？", questions[0].question)
    }

    @Test
    fun `accepts bare json array`() {
        val extractor = QuestionExtractor(FakeAiClient.returning(""))
        val raw = """[{"question":"讲讲 View 的绘制流程","tags":["Android"]}]"""

        val questions = extractor.parse(raw)

        assertEquals(1, questions.size)
        assertEquals("讲讲 View 的绘制流程", questions[0].question)
    }

    @Test
    fun `skips items without question text`() {
        val extractor = QuestionExtractor(FakeAiClient.returning(""))
        val raw = """{"questions":[{"question":"","tags":["x"]},{"question":"有效题目"}]}"""

        val questions = extractor.parse(raw)

        assertEquals(1, questions.size)
        assertEquals("有效题目", questions[0].question)
    }

    @Test
    fun `returns empty when no questions`() {
        val extractor = QuestionExtractor(FakeAiClient.returning(""))

        val questions = extractor.parse("""{"questions":[]}""")

        assertTrue(questions.isEmpty())
    }

    @Test
    fun `throws AiException on malformed output`() {
        val extractor = QuestionExtractor(FakeAiClient.returning(""))

        assertThrows(AiException::class.java) {
            extractor.parse("这不是 JSON，模型答非所问")
        }
    }

    @Test
    fun `buildMessages injects post text and requires json`() {
        val extractor = QuestionExtractor(FakeAiClient.returning(""))

        val messages = extractor.buildMessages("这是一段面经正文，问了 Activity 生命周期。")

        assertEquals(Role.SYSTEM, messages.first().role)
        assertTrue("系统提示应要求仅输出 JSON", messages.first().content.contains("JSON"))
        assertEquals(Role.USER, messages.last().role)
        assertTrue("用户消息应包含帖子正文", messages.last().content.contains("Activity 生命周期"))
    }

    @Test
    fun `extract calls ai client with built messages and parses response`() = runTest {
        val fake = FakeAiClient.returning(loadFixture("extract_response.json"))
        val extractor = QuestionExtractor(fake)

        val questions = extractor.extract("一段包含面试题的面经正文")

        assertEquals(3, questions.size)
        // 校验确实用构造的消息调用了模型
        assertTrue(fake.lastMessages.last().content.contains("面经正文"))
    }
}
