package com.jk.offermate.agent

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CategoryClassifierTest {

    private fun q(text: String) = AnsweredQuestion(question = text, answer = "")

    @Test
    fun `prompt includes existing categories and questions`() {
        val fake = FakeAiClient.returning("[]")
        val classifier = CategoryClassifier(fake)

        classifier.buildMessages(
            questions = listOf(q("Handler 原理"), q("TCP 三次握手")),
            existingCategories = listOf("Android", "计算机网络")
        ).let { messages ->
            val user = messages.last().content
            assertTrue(user.contains("Android"))
            assertTrue(user.contains("计算机网络"))
            assertTrue(user.contains("0. Handler 原理"))
            assertTrue(user.contains("1. TCP 三次握手"))
        }
    }

    @Test
    fun `classify assigns categories by index`() = runTest {
        val fake = FakeAiClient.returning(
            """[{"index":0,"category":"Android"},{"index":1,"category":"计算机网络"}]"""
        )
        val classifier = CategoryClassifier(fake)

        val result = classifier.classify(
            questions = listOf(q("Handler 原理"), q("TCP 三次握手")),
            existingCategories = listOf("Android")
        )

        assertEquals("Android", result[0].category)
        assertEquals("计算机网络", result[1].category)
    }

    @Test
    fun `parse tolerates code fence and missing entries`() {
        val classifier = CategoryClassifier(FakeAiClient.returning(""))
        val raw = "```json\n[{\"index\":1,\"category\":\"Java\"}]\n```"
        val cats = classifier.parse(raw, size = 3)
        assertEquals(listOf("", "Java", ""), cats)
    }

    @Test
    fun `empty questions returns unchanged without calling model`() = runTest {
        val fake = FakeAiClient.returning("should-not-be-used")
        val classifier = CategoryClassifier(fake)
        val result = classifier.classify(emptyList(), listOf("Android"))
        assertTrue(result.isEmpty())
        assertTrue(fake.recordedMessages.isEmpty())
    }
}
