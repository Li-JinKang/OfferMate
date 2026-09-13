package com.jk.offermate.agent

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

    // ---- 可重试性分类（见 docs/plan/network-resilience.md 第 4 节）----

    @Test
    fun `401 is classified as non-retryable and points at the api key`() {
        server.enqueue(MockResponse().setResponseCode(401).setBody("""{"error":"invalid key"}"""))

        val e = assertThrows(AiException::class.java) {
            runBlocking { client().chat(listOf(ChatMessage(Role.USER, "hi"))) }
        }

        assertFalse(e.retryable)
        assertTrue(e.message!!.contains("API Key"))
    }

    @Test
    fun `429 is classified as retryable`() {
        server.enqueue(MockResponse().setResponseCode(429).setBody("rate limited"))

        val e = assertThrows(AiException::class.java) {
            runBlocking { client().chat(listOf(ChatMessage(Role.USER, "hi"))) }
        }

        assertTrue(e.retryable)
    }

    @Test
    fun `5xx is classified as retryable`() {
        server.enqueue(MockResponse().setResponseCode(503).setBody("unavailable"))

        val e = assertThrows(AiException::class.java) {
            runBlocking { client().chat(listOf(ChatMessage(Role.USER, "hi"))) }
        }

        assertTrue(e.retryable)
    }

    /**
     * 本次事故的核心回归：连接被掀掉时不再裸抛 IOException 穿透到 Worker，
     * 而是包成 retryable 的 [AiException]，让任务层能走 `Result.retry()`。
     */
    @Test
    fun `connection reset becomes retryable AiException instead of raw IOException`() {
        // 两次都断开：第二条覆盖"疑似 H2 中断 → 降级 HTTP/1.1 重试一次"也失败的路径
        repeat(2) { server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START)) }

        val e = assertThrows(AiException::class.java) {
            runBlocking { client().chat(listOf(ChatMessage(Role.USER, "hi"))) }
        }

        assertTrue(e.retryable)
        assertTrue(e.cause is java.io.IOException)
    }

    @Test
    fun `blank api key is not retryable`() {
        val e = assertThrows(AiException::class.java) {
            runBlocking { client(apiKey = "  ").chat(listOf(ChatMessage(Role.USER, "hi"))) }
        }

        assertFalse(e.retryable)
    }

    @Test
    fun `missing choices throws AiException`() {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"id":"x"}"""))

        assertThrows(AiException::class.java) {
            runBlocking { client().chat(listOf(ChatMessage(Role.USER, "hi"))) }
        }
    }
}
