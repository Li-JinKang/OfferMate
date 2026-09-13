package com.jk.offermate.work

import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/**
 * 导入分析的并发闸门：同一时刻只允许一个帖子在跑 AI 分析。
 *
 * 起因（见 docs/plan/network-resilience.md 第 1.1 节）：两个 `AnalyzePostWorker` 并发时，
 * 共享同一个 `OkHttpClient` 的连接池，请求在**同一条 HTTP/2 连接上多路复用**——
 * 连接一断，两个任务在同一毫秒双双失败。串行化后故障被隔离在单个任务内，
 * 同时也避免并发烧 token 与触发服务端限流。
 *
 * WorkManager 默认并发度为 4，闸门在进程内生效即可（worker 都在主进程）。
 */
object AnalyzeGate {

    private val semaphore = Semaphore(permits = 1)

    /** 排队执行 [block]；等待期间协程挂起，不占线程。 */
    suspend fun <T> withSlot(block: suspend () -> T): T = semaphore.withPermit { block() }
}
