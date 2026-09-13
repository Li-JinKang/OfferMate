package com.jk.offermate.data.net

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger

/**
 * 覆盖 docs/plan/network-resilience.md 第 9 节验证矩阵里的"瞬时连接重置"与"确定性错误不重试"。
 */
class RetryInterceptorTest {

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

    /** 记录 [RetryInterceptor] 实际向下游发起了几次尝试。 */
    private val attempts = AtomicInteger(0)

    /**
     * 退避不真的睡，避免单测变慢。
     *
     * 尝试次数用下游计数拦截器统计，而不是 `server.requestCount`：
     * `DISCONNECT_AT_START` 在读到请求前就断开，MockWebServer 根本不会计数。
     */
    private fun client(maxAttempts: Int = 3, sleeper: (Long) -> Unit = {}) = OkHttpClient.Builder()
        // 关掉 OkHttp 自带的路由级重试，确保观测到的次数只来自 RetryInterceptor
        .retryOnConnectionFailure(false)
        .addInterceptor(RetryInterceptor(maxAttempts = maxAttempts, sleeper = sleeper))
        .addInterceptor { chain ->
            attempts.incrementAndGet()
            chain.proceed(chain.request())
        }
        .build()

    /**
     * 显式走 IPv4 回环：MockWebServer 只监听 127.0.0.1，而 `localhost` 在本机可能先解析到 ::1。
     * 由于这里刻意关掉了 OkHttp 的路由级重试，地址回退不会发生，必须直接指定 127.0.0.1。
     */
    private fun request() = Request.Builder()
        .url(server.url("/v1/chat").newBuilder().host("127.0.0.1").build())
        .build()

    @Test
    fun `retries after connection is reset and then succeeds`() {
        // DISCONNECT_AFTER_REQUEST：请求已发出、响应途中连接被掀掉，
        // 与线上 `SocketException: Software caused connection abort` 同形。
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST))
        server.enqueue(MockResponse().setResponseCode(200).setBody("ok"))

        client().newCall(request()).execute().use { resp ->
            assertEquals(200, resp.code)
            assertEquals("ok", resp.body!!.string())
        }
        assertEquals(2, attempts.get())
    }

    @Test
    fun `retries on 5xx then returns success`() {
        server.enqueue(MockResponse().setResponseCode(503))
        server.enqueue(MockResponse().setResponseCode(200).setBody("ok"))

        client().newCall(request()).execute().use { resp ->
            assertEquals(200, resp.code)
        }
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `gives up after maxAttempts and returns last response`() {
        repeat(3) { server.enqueue(MockResponse().setResponseCode(500)) }

        client(maxAttempts = 3).newCall(request()).execute().use { resp ->
            assertEquals(500, resp.code)
        }
        assertEquals(3, server.requestCount)
    }

    @Test
    fun `does not retry deterministic 401`() {
        server.enqueue(MockResponse().setResponseCode(401).setBody("invalid key"))

        client().newCall(request()).execute().use { resp ->
            assertEquals(401, resp.code)
        }
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `rethrows IOException when all attempts fail`() {
        repeat(3) { server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST)) }

        assertThrows(IOException::class.java) {
            client(maxAttempts = 3).newCall(request()).execute()
        }
        assertEquals(3, attempts.get())
    }

    @Test
    fun `honours Retry-After on 429`() {
        server.enqueue(MockResponse().setResponseCode(429).setHeader("Retry-After", "1"))
        server.enqueue(MockResponse().setResponseCode(200).setBody("ok"))

        var sleptMillis = 0L
        val c = OkHttpClient.Builder()
            .retryOnConnectionFailure(false)
            .addInterceptor(RetryInterceptor(maxAttempts = 2, sleeper = { sleptMillis = it }))
            .build()

        c.newCall(request()).execute().use { resp -> assertEquals(200, resp.code) }

        assertEquals(1000L, sleptMillis)
    }
}
