package com.jk.offermate.data.net

import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * 全局 OkHttp 供给。治理背景见 docs/plan/network-resilience.md。
 *
 * 此前四处各自 `OkHttpClient.Builder()`（LLM / MCP / 页面抓取 / 图片下载），连接池互不复用、
 * 零拦截器、零观测。统一到这里后：
 * - 共享一个 [ConnectionPool]，连接数可控，降级策略有统一落点；
 * - 统一挂 [NetEventListener]，失败发生在哪个阶段、走的是 H2 还是 H1.1 一目了然；
 * - 需要重试的档位统一挂 [RetryInterceptor]。
 *
 * 分档原则：**读超时按"服务端思考时间"给**，不是按网络快慢给。
 */
object HttpClients {

    /** 共享连接池：并发任务不多，5 分钟保活足够覆盖一次导入内的多次模型调用。 */
    private val sharedPool = ConnectionPool(
        /* maxIdleConnections = */ 8,
        /* keepAliveDuration = */ 5,
        TimeUnit.MINUTES
    )

    private fun base(): OkHttpClient.Builder = OkHttpClient.Builder()
        .connectionPool(sharedPool)
        // 默认即为 true，显式写出以声明"路由级重试"是被期望的行为
        .retryOnConnectionFailure(true)
        // HTTP/2 保活：死连接由 ping 及早暴露，而不是卡到读超时才报错
        .pingInterval(PING_INTERVAL_SECONDS, TimeUnit.SECONDS)
        .eventListenerFactory(NetEventListener.FACTORY)

    /**
     * LLM 非流式调用：生成长文本慢，读超时给足；**不设 callTimeout**——
     * callTimeout 统计整次 call 的墙钟时间，会把 [RetryInterceptor] 的后续 attempt 砍掉。
     */
    val llm: OkHttpClient by lazy {
        base()
            .connectTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .addInterceptor(RetryInterceptor(maxAttempts = 3))
            .build()
    }

    /**
     * LLM 流式（SSE）调用：**不挂 [RetryInterceptor]**。
     * 增量已经透出后再重试会产生重复文本，其瞬时失败交给任务层整轮重试。
     */
    val llmStream: OkHttpClient by lazy {
        base()
            .connectTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .build()
    }

    /** MCP 工具调用：外部服务，重试一次即可。 */
    val mcp: OkHttpClient by lazy {
        base()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .addInterceptor(RetryInterceptor(maxAttempts = 2))
            .build()
    }

    /** 短链展开 / HTML 抓取：跟随重定向，超时短，失败即走手动粘贴兜底。 */
    val page: OkHttpClient by lazy {
        base()
            .followRedirects(true)
            .followSslRedirects(true)
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .addInterceptor(RetryInterceptor(maxAttempts = 2))
            .build()
    }

    /** 图片下载（供 OCR）：面经长图体积大，读超时放宽。 */
    val image: OkHttpClient by lazy {
        base()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .addInterceptor(RetryInterceptor(maxAttempts = 2))
            .build()
    }

    private const val PING_INTERVAL_SECONDS = 20L
}
