package com.jk.offermate.data.net

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLHandshakeException

class NetErrorsTest {

    /** 本次事故的原始异常：SocketException: Software caused connection abort。 */
    private val connectionAbort = SocketException("Software caused connection abort")

    @Test
    fun `connection abort is transient and recognised as abort`() {
        assertTrue(NetErrors.isTransient(connectionAbort))
        assertTrue(NetErrors.looksLikeConnectionAbort(connectionAbort))
        assertEquals("网络连接被中断", NetErrors.userMessage(connectionAbort))
    }

    @Test
    fun `finds cause through wrapper exceptions`() {
        val wrapped = IllegalStateException("分析失败", RuntimeException("inner", connectionAbort))

        assertTrue(NetErrors.isTransient(wrapped))
        assertTrue(NetErrors.looksLikeConnectionAbort(wrapped))
    }

    @Test
    fun `timeout and unknown host are transient`() {
        assertTrue(NetErrors.isTransient(SocketTimeoutException("timeout")))
        assertTrue(NetErrors.isTransient(UnknownHostException("api.deepseek.com")))
        assertEquals("网络超时，请稍后重试", NetErrors.userMessage(SocketTimeoutException("timeout")))
    }

    @Test
    fun `tls handshake failure is not transient`() {
        val tls = SSLHandshakeException("cert not trusted")

        assertFalse(NetErrors.isTransient(tls))
        assertEquals("安全连接失败（证书校验未通过）", NetErrors.userMessage(tls))
    }

    @Test
    fun `non-io errors are not transient`() {
        assertFalse(NetErrors.isTransient(IllegalArgumentException("模型输出中未找到有效 JSON")))
        assertEquals("模型输出中未找到有效 JSON", NetErrors.userMessage(IllegalArgumentException("模型输出中未找到有效 JSON")))
    }

    @Test
    fun `plain io exception falls back to generic network message`() {
        assertTrue(NetErrors.isTransient(IOException("stream closed unexpectedly")))
        assertTrue(NetErrors.userMessage(IOException("boom")).startsWith("网络"))
    }

    @Test
    fun `null throwable yields unknown message`() {
        assertFalse(NetErrors.isTransient(null))
        assertEquals("未知错误", NetErrors.userMessage(null))
    }
}
