package com.jk.offermate.data.net

import java.io.IOException
import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLHandshakeException
import javax.net.ssl.SSLPeerUnverifiedException

/**
 * 网络异常分类与文案。分类契约见 docs/plan/network-resilience.md 第 4 节。
 *
 * 存在的意义：`SocketException: Software caused connection abort` 这类**瞬时**故障，
 * 此前被当成终态失败一次性判死；而证书错误、未知主机这类**确定性**故障重试也没用。
 * 两者必须区分开，否则要么白等要么白烧 token。
 */
object NetErrors {

    /** 是否为可重试的瞬时网络故障（会沿 cause 链查找）。 */
    fun isTransient(throwable: Throwable?): Boolean = findCause(throwable) { candidate ->
        when (candidate) {
            // 证书/校验类问题重试无用
            is SSLHandshakeException, is SSLPeerUnverifiedException -> false
            // DNS 解析不到：多为无网或域名被污染，一次重试意义不大，但代价也低，归为瞬时
            is UnknownHostException -> true
            is SocketTimeoutException, is InterruptedIOException -> true
            is ConnectException, is SocketException -> true
            is IOException -> true
            else -> false
        }
    }

    /**
     * 是否像"连接被对端/中间设备掀掉"。这是 HTTP/2 长连接在国内网络 + 代理环境下的高发故障，
     * 用于触发 [Http2Health] 的协议降级。
     */
    fun looksLikeConnectionAbort(throwable: Throwable?): Boolean = findCause(throwable) { candidate ->
        val name = candidate.javaClass.simpleName
        val message = candidate.message.orEmpty().lowercase()
        name == "StreamResetException" ||
            name == "ConnectionShutdownException" ||
            (candidate is SocketException && ABORT_MARKERS.any { it in message })
    }

    /** 面向用户的简短文案，用于失败徽章与通知。 */
    fun userMessage(throwable: Throwable?): String {
        val cause = firstCause(throwable)
        return when {
            cause is UnknownHostException -> "网络不可用（域名解析失败）"
            cause is SocketTimeoutException -> "网络超时，请稍后重试"
            cause is SSLHandshakeException || cause is SSLPeerUnverifiedException -> "安全连接失败（证书校验未通过）"
            looksLikeConnectionAbort(throwable) -> "网络连接被中断"
            cause is IOException -> "网络异常：${cause.message ?: cause.javaClass.simpleName}"
            else -> throwable?.message?.takeIf { it.isNotBlank() } ?: "未知错误"
        }
    }

    private inline fun findCause(throwable: Throwable?, predicate: (Throwable) -> Boolean): Boolean {
        var current = throwable
        var depth = 0
        while (current != null && depth < MAX_CAUSE_DEPTH) {
            if (predicate(current)) return true
            current = current.cause?.takeIf { it !== current }
            depth++
        }
        return false
    }

    /** 取 cause 链上第一个 [IOException]，没有则返回原异常。 */
    private fun firstCause(throwable: Throwable?): Throwable? {
        var current = throwable
        var depth = 0
        while (current != null && depth < MAX_CAUSE_DEPTH) {
            if (current is IOException) return current
            current = current.cause?.takeIf { it !== current }
            depth++
        }
        return throwable
    }

    private const val MAX_CAUSE_DEPTH = 8
    private val ABORT_MARKERS = listOf("abort", "reset", "broken pipe", "closed")
}
