package com.jk.offermate.data.net

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class Http2HealthTest {

    private val host = "api.deepseek.com"

    @Before
    fun setUp() = Http2Health.reset()

    @Test
    fun `single abort does not degrade`() {
        Http2Health.recordAbort(host)

        assertFalse(Http2Health.shouldAvoidHttp2(host))
    }

    @Test
    fun `two aborts degrade the host`() {
        Http2Health.recordAbort(host)
        Http2Health.recordAbort(host)

        assertTrue(Http2Health.shouldAvoidHttp2(host))
    }

    @Test
    fun `success resets the counter`() {
        Http2Health.recordAbort(host)
        Http2Health.recordSuccess(host)
        Http2Health.recordAbort(host)

        assertFalse(Http2Health.shouldAvoidHttp2(host))
    }

    @Test
    fun `degradation expires after the window`() {
        val now = 1_000_000L
        Http2Health.recordAbort(host, now)
        Http2Health.recordAbort(host, now)

        assertTrue(Http2Health.shouldAvoidHttp2(host, now + 60_000))
        assertFalse(Http2Health.shouldAvoidHttp2(host, now + 11 * 60_000))
    }

    @Test
    fun `degradation is per host`() {
        Http2Health.recordAbort(host)
        Http2Health.recordAbort(host)

        assertTrue(Http2Health.shouldAvoidHttp2(host))
        assertFalse(Http2Health.shouldAvoidHttp2("example.com"))
    }
}
