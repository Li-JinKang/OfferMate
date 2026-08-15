package com.jk.offermate.data.reader

import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class UrlResolverTest {

    private lateinit var server: MockWebServer
    private val resolver = OkHttpUrlResolver()

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `expands short link by following redirect`() {
        val finalUrl = server.url("/note/real-content").toString()
        server.enqueue(
            MockResponse()
                .setResponseCode(302)
                .setHeader("Location", finalUrl)
        )
        server.enqueue(MockResponse().setResponseCode(200).setBody("ok"))

        val resolved = resolver.resolve(server.url("/o/shortcode").toString())

        assertEquals(finalUrl, resolved)
    }

    @Test
    fun `follows multiple redirects to final url`() {
        val finalUrl = server.url("/final").toString()
        server.enqueue(MockResponse().setResponseCode(301).setHeader("Location", server.url("/mid").toString()))
        server.enqueue(MockResponse().setResponseCode(302).setHeader("Location", finalUrl))
        server.enqueue(MockResponse().setResponseCode(200).setBody("ok"))

        val resolved = resolver.resolve(server.url("/start").toString())

        assertTrue(resolved.endsWith("/final"))
    }

    @Test
    fun `returns original url on failure`() {
        val bogus = "http://127.0.0.1:1/does-not-exist"

        val resolved = resolver.resolve(bogus)

        assertEquals(bogus, resolved)
    }
}
