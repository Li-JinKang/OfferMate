package com.jk.offermate.agent.pipeline

import com.jk.offermate.agent.FakeAiClient
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AnalysisPipelineTest {

    private fun loadFixture(name: String): String =
        requireNotNull(javaClass.getResourceAsStream("/fixtures/llm/$name")) {
            "缺少测试夹具: $name"
        }.bufferedReader().use { it.readText() }

    private fun pipeline(
        extractResponse: String,
        relevanceResponse: String = "",
        answerResponse: String = ""
    ) = AnalysisPipeline(
        extractor = QuestionExtractor(FakeAiClient.returning(extractResponse)),
        matcher = RelevanceMatcher(FakeAiClient.returning(relevanceResponse)),
        answerer = AnswerGenerator(FakeAiClient.returning(answerResponse))
    )

    @Test
    fun `full pipeline extracts filters and answers`() = runTest {
        val pipeline = pipeline(
            extractResponse = loadFixture("extract_response.json"),
            relevanceResponse = loadFixture("relevance_response.json"),
            answerResponse = loadFixture("answer_response.json")
        )

        val answered = pipeline.analyze("一段面经正文")

        assertEquals(2, answered.size)
        assertEquals(95, answered[0].relevanceScore)
        assertEquals(70, answered[1].relevanceScore)
        assertTrue(answered[0].question.contains("Activity"))
        assertTrue(answered[0].answer.isNotBlank())
    }

    @Test
    fun `returns empty when no questions extracted`() = runTest {
        val pipeline = pipeline(extractResponse = """{"questions":[]}""")

        val answered = pipeline.analyze("没有题目的正文")

        assertTrue(answered.isEmpty())
    }

    @Test
    fun `returns empty when no question is relevant`() = runTest {
        val pipeline = pipeline(
            extractResponse = loadFixture("extract_response.json"),
            // 全部低于默认阈值 60
            relevanceResponse = """{"results":[{"index":0,"score":20,"reason":"弱"},{"index":1,"score":10,"reason":"弱"},{"index":2,"score":5,"reason":"弱"}]}"""
        )

        val answered = pipeline.analyze("一段面经正文")

        assertTrue(answered.isEmpty())
    }
}
