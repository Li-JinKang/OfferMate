package com.jk.offermate.data.ai

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class DeepSeekClientTest {

    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun client(apiKey: String = "sk-test", model: String = "deepseek-chat") = DeepSeekClient(
        apiKeyProvider = { apiKey },
        modelProvider = { model },
        baseUrlProvider = { server.url("/").toString() },
        client = OkHttpClient()
    )

    @Test
    fun `sends correct request and parses assistant content`() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"choices":[{"message":{"role":"assistant","content":"你好，这是答案"}}]}"""
            )
        )

        val result = client().chat(listOf(ChatMessage(Role.USER, "讲讲协程")))

        assertEquals("你好，这是答案", result)

        val recorded = server.takeRequest()
        assertTrue(recorded.path!!.endsWith("/chat/completions"))
        assertEquals("Bearer sk-test", recorded.getHeader("Authorization"))
        val body = recorded.body.readUtf8()
        assertTrue(body.contains("deepseek-chat"))
        assertTrue(body.contains("\"role\":\"user\""))
        assertTrue(body.contains("讲讲协程"))
    }

    @Test
    fun `blank api key throws without hitting network`() {
        val c = client(apiKey = "   ")

        assertThrows(AiException::class.java) {
            runBlocking { c.chat(listOf(ChatMessage(Role.USER, "hi"))) }
        }
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `non-2xx response throws AiException`() {
        server.enqueue(MockResponse().setResponseCode(401).setBody("""{"error":"invalid key"}"""))

        assertThrows(AiException::class.java) {
            runBlocking { client().chat(listOf(ChatMessage(Role.USER, "hi"))) }
        }
    }

    @Test
    fun `missing choices throws AiException`() {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"id":"x"}"""))

        assertThrows(AiException::class.java) {
            runBlocking { client().chat(listOf(ChatMessage(Role.USER, "hi"))) }
        }
    }
}
