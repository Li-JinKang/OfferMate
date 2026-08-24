package com.jk.offermate.agent.tool

import com.jk.offermate.agent.pipeline.AnsweredQuestion
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 本地赋能工具：search_questions / list_categories 的纯逻辑测试。 */
class LocalToolsTest {

    private val bank = listOf(
        AnsweredQuestion(question = "什么是协程", answer = "轻量级线程…", category = "Kotlin"),
        AnsweredQuestion(question = "Handler 原理", answer = "消息队列…", category = "Android"),
        AnsweredQuestion(question = "ThreadLocal 用途", answer = "线程隔离变量…", category = "Java")
    )

    // --- search_questions ---

    @Test
    fun `search returns matching questions with answers`() = runTest {
        val tool = QuestionSearchTool(search = { q, limit ->
            bank.filter { it.question.contains(q) || it.category.contains(q) }.take(limit)
        })
        val out = tool.call("""{"query":"协程"}""")
        assertTrue(out.contains("什么是协程"))
        assertTrue(out.contains("轻量级线程"))
        assertFalse(out.contains("Handler"))
    }

    @Test
    fun `search empty query is guarded`() = runTest {
        var called = false
        val tool = QuestionSearchTool(search = { _, _ -> called = true; emptyList() })
        val out = tool.call("""{"query":"   "}""")
        assertTrue(out.contains("未提供检索关键词"))
        assertFalse(called)
    }

    @Test
    fun `search reports no hits`() = runTest {
        val tool = QuestionSearchTool(search = { _, _ -> emptyList() })
        assertTrue(tool.call("""{"query":"rust"}""").contains("未找到"))
    }

    @Test
    fun `search respects limit argument`() = runTest {
        var seenLimit = -1
        val tool = QuestionSearchTool(search = { _, limit -> seenLimit = limit; bank.take(limit) })
        tool.call("""{"query":"a","limit":2}""")
        assertEquals(2, seenLimit)
    }

    @Test
    fun `search truncates long answers`() = runTest {
        val long = "x".repeat(1000)
        val tool = QuestionSearchTool(
            search = { _, _ -> listOf(AnsweredQuestion(question = "q", answer = long)) },
            maxAnswerChars = 100
        )
        val out = tool.call("""{"query":"q"}""")
        assertTrue(out.contains("…"))
        assertFalse(out.contains(long))
    }

    @Test
    fun `search spec name`() {
        assertEquals("search_questions", QuestionSearchTool(search = { _, _ -> emptyList() }).spec.name)
    }

    // --- list_categories ---

    @Test
    fun `list categories distinct and trimmed`() = runTest {
        val tool = CategoryListTool(categoriesProvider = { listOf("Kotlin", " Android ", "Kotlin", "") })
        val out = tool.call("{}")
        assertTrue(out.contains("Kotlin"))
        assertTrue(out.contains("Android"))
        // 去重后仅一个 Kotlin
        assertEquals(1, Regex("Kotlin").findAll(out).count())
    }

    @Test
    fun `list categories empty`() = runTest {
        val tool = CategoryListTool(categoriesProvider = { emptyList() })
        assertTrue(tool.call("{}").contains("暂无分类"))
    }
}
