package com.jk.offermate.agent

import com.jk.offermate.data.memory.MemoryProfileEntry
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileMatcherTest {

    private val existing = listOf(
        MemoryProfileEntry("java-backend", "Java 后端", "Java 后端开发"),
        MemoryProfileEntry("android", "Android", "Android 开发")
    )

    @Test
    fun `parses matched id`() {
        val m = ProfileMatcher(FakeAiClient.returning("")).parse(
            """{"matchedId":"java-backend","reason":"同为后端"}""", existing
        )
        assertEquals("java-backend", m.matchedId)
        assertEquals("同为后端", m.reason)
    }

    @Test
    fun `null matchedId means new`() {
        val m = ProfileMatcher(FakeAiClient.returning("")).parse("""{"matchedId":null}""", existing)
        assertNull(m.matchedId)
    }

    @Test
    fun `unknown id is treated as new`() {
        val m = ProfileMatcher(FakeAiClient.returning("")).parse("""{"matchedId":"golang"}""", existing)
        assertNull(m.matchedId)
    }

    @Test
    fun `empty existing returns new without calling ai`() = runTest {
        val ai = FakeAiClient.returning("""{"matchedId":"java-backend"}""")
        val m = ProfileMatcher(ai).match("Java 后端开发", emptyList())
        assertNull(m.matchedId)
        assertTrue(ai.recordedMessages.isEmpty())
    }

    @Test
    fun `match calls ai when existing present`() = runTest {
        val ai = FakeAiClient.returning("""{"matchedId":"android","reason":"同为安卓"}""")
        val m = ProfileMatcher(ai).match("Android 工程师", existing)
        assertEquals("android", m.matchedId)
        assertTrue(ai.recordedMessages.isNotEmpty())
    }
}
