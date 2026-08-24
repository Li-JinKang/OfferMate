package com.jk.offermate.ui.quiz

import com.jk.offermate.agent.pipeline.AnsweredQuestion
import org.junit.Assert.assertEquals
import org.junit.Test

class CategoryResolverTest {

    private fun q(category: String = "", tags: List<String> = emptyList()) =
        AnsweredQuestion(question = "q", answer = "", tags = tags, category = category)

    @Test
    fun `uses llm assigned category when present`() {
        assertEquals("Android", CategoryResolver.displayCategory(q(category = "Android", tags = listOf("Handler"))))
    }

    @Test
    fun `falls back to first tag when category blank`() {
        assertEquals("Handler", CategoryResolver.displayCategory(q(category = "  ", tags = listOf("Handler", "Looper"))))
    }

    @Test
    fun `falls back to other when nothing available`() {
        assertEquals(CategoryResolver.OTHER, CategoryResolver.displayCategory(q()))
    }
}
