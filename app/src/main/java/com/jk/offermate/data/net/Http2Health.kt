package com.jk.offermate.data.net

import android.util.Log
import java.util.concurrent.ConcurrentHashMap

/**
 * 按 host 记录 HTTP/2 连接被中断的次数，达到阈值后建议降级到 HTTP/1.1 一段时间。
 *
 * 为什么需要：`Software caused connection abort` 在国内网络 / VPN / 企业代理下对 H2 长连接高发，
 * 而且一条连接上多路复用的所有请求会**同时**阵亡（本次事故里两个分析任务在同一毫秒双双失败）。
 * 降级为 H1.1 后每个请求独占连接，故障被隔离在单个请求内。
 *
 * 线程安全，进程内生效，不持久化（重启后重新探测）。
 */
object Http2Health {

    private val abortCounts = ConcurrentHashMap<String, Int>()
    private val degradedUntil = ConcurrentHashMap<String, Long>()

    /** 记录一次疑似连接被掀掉的失败；达到阈值则把该 host 标记为降级。 */
    fun recordAbort(host: String, nowMillis: Long = System.currentTimeMillis()) {
        val count = abortCounts.merge(host, 1, Int::plus) ?: 1
        if (count >= ABORT_THRESHOLD) {
            degradedUntil[host] = nowMillis + DEGRADE_WINDOW_MILLIS
            abortCounts.remove(host)
            Log.w(TAG, "host=$host 连续 $count 次连接中断，接下来 ${DEGRADE_WINDOW_MILLIS / 60_000} 分钟降级为 HTTP/1.1")
        }
    }

    /** 记录一次成功，清空该 host 的失败计数。 */
    fun recordSuccess(host: String) {
        abortCounts.remove(host)
    }

    /** 该 host 当前是否应避免使用 HTTP/2。 */
    fun shouldAvoidHttp2(host: String, nowMillis: Long = System.currentTimeMillis()): Boolean {
        val until = degradedUntil[host] ?: return false
        if (nowMillis >= until) {
            degradedUntil.remove(host)
            return false
        }
        return true
    }

    /** 仅供测试重置状态。 */
    fun reset() {
        abortCounts.clear()
        degradedUntil.clear()
    }

    private const val TAG = "OfferMateNet"
    private const val ABORT_THRESHOLD = 2
    private const val DEGRADE_WINDOW_MILLIS = 10 * 60 * 1000L
}
