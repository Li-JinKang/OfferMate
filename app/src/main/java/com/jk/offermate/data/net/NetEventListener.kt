package com.jk.offermate.data.net

import android.util.Log
import okhttp3.Call
import okhttp3.Connection
import okhttp3.EventListener
import okhttp3.Protocol
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Proxy

/**
 * 轻量网络阶段观测：只在**失败**和**慢请求**时打日志，正常请求不产生噪音。
 *
 * 排查"连接被重置"这类问题时，需要知道失败发生在哪个阶段（DNS / TCP / TLS / 读响应），
 * 以及用的是 HTTP/2 还是 HTTP/1.1——这两点原先在日志里完全缺失。
 */
class NetEventListener : EventListener() {

    private var startNanos = 0L
    private var protocol: Protocol? = null

    override fun callStart(call: Call) {
        startNanos = System.nanoTime()
    }

    override fun connectionAcquired(call: Call, connection: Connection) {
        protocol = connection.protocol()
    }

    override fun dnsEnd(call: Call, domainName: String, inetAddressList: List<java.net.InetAddress>) {
        if (inetAddressList.isEmpty()) Log.w(TAG, "DNS 解析为空 host=$domainName")
    }

    override fun connectFailed(
        call: Call,
        inetSocketAddress: InetSocketAddress,
        proxy: Proxy,
        protocol: Protocol?,
        ioe: IOException
    ) {
        Log.w(
            TAG,
            "连接失败 addr=$inetSocketAddress proxy=${proxy.type()} protocol=$protocol " +
                "err=${ioe.javaClass.simpleName}:${ioe.message}"
        )
    }

    override fun callFailed(call: Call, ioe: IOException) {
        Log.w(
            TAG,
            "请求失败 ${call.request().method} ${call.request().url.redact()} " +
                "protocol=${protocol ?: "未建连"} 耗时=${elapsedMillis()}ms " +
                "err=${ioe.javaClass.simpleName}:${ioe.message}"
        )
    }

    override fun callEnd(call: Call) {
        val ms = elapsedMillis()
        if (ms >= SLOW_CALL_MILLIS) {
            Log.i(TAG, "慢请求 ${call.request().url.redact()} protocol=${protocol} 耗时=${ms}ms")
        }
    }

    private fun elapsedMillis(): Long =
        if (startNanos == 0L) -1 else (System.nanoTime() - startNanos) / 1_000_000

    companion object {
        const val TAG = "OfferMateNet"

        /** 超过该阈值记一条 info，便于定位模型侧慢生成与网络慢的区别。 */
        const val SLOW_CALL_MILLIS = 20_000L

        val FACTORY: EventListener.Factory = EventListener.Factory { NetEventListener() }
    }
}
