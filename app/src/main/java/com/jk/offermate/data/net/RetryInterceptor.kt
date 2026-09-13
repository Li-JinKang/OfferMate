package com.jk.offermate.data.net

import android.util.Log
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException
import kotlin.math.pow
import kotlin.random.Random

/**
 * 应用层重试：对**瞬时**失败做指数退避 + 抖动重试。分类契约见 docs/plan/network-resilience.md 第 4 节。
 *
 * 可重试：所有 [IOException]（连接重置 / 读超时 / DNS 失败）、408、429、5xx。
 * 不可重试：401/402/403/404 等确定性错误——重试只会浪费时间与 token。
 *
 * 注意事项：
 * - **不要用于流式（SSE）请求**：增量已经透出后再重试会产生重复文本。
 * - 重试前必须 `response.close()`，否则连接泄漏。
 * - 拦截器运行在 OkHttp 的 IO 线程，可以安全阻塞。
 * - 与 `callTimeout` 互斥：`callTimeout` 统计整次 call 的墙钟时间，会把后续 attempt 砍掉。
 */
class RetryInterceptor(
    private val maxAttempts: Int = 3,
    private val baseDelayMillis: Long = 500L,
    private val maxDelayMillis: Long = 8_000L,
    private val sleeper: (Long) -> Unit = { Thread.sleep(it) }
) : Interceptor {

    init {
        require(maxAttempts >= 1) { "maxAttempts 至少为 1" }
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        var lastIoException: IOException? = null

        for (attempt in 0 until maxAttempts) {
            val isLast = attempt == maxAttempts - 1
            var retryAfterMillis: Long? = null

            try {
                val response = chain.proceed(chain.request())
                if (!isRetryableCode(response.code) || isLast) return response

                retryAfterMillis = parseRetryAfter(response)
                Log.w(TAG, "HTTP ${response.code}，第 ${attempt + 1}/$maxAttempts 次将重试 ${chain.request().url}")
                // 丢弃响应体，释放连接后再重试
                response.close()
            } catch (e: IOException) {
                lastIoException = e
                Log.w(TAG, "IO 失败（${e.javaClass.simpleName}: ${e.message}），第 ${attempt + 1}/$maxAttempts 次", e)
                if (isLast) throw e
            }

            sleeper(retryAfterMillis ?: backoffMillis(attempt))
        }

        // 循环结构上不会走到这里（最后一次必然 return 或 throw），仅为编译期完备。
        throw lastIoException ?: IOException("重试次数已用尽")
    }

    /** 指数退避 + 满抖动，避免多任务同时恢复造成惊群。 */
    private fun backoffMillis(attempt: Int): Long {
        val exponential = (baseDelayMillis * 2.0.pow(attempt)).toLong().coerceAtMost(maxDelayMillis)
        return exponential / 2 + Random.nextLong(exponential / 2 + 1)
    }

    private fun parseRetryAfter(response: Response): Long? =
        response.header("Retry-After")
            ?.trim()
            ?.toLongOrNull()
            ?.takeIf { it in 0..MAX_RETRY_AFTER_SECONDS }
            ?.let { it * 1000 }

    private companion object {
        const val TAG = "OfferMateNet"
        const val MAX_RETRY_AFTER_SECONDS = 30L

        fun isRetryableCode(code: Int): Boolean = code == 408 || code == 429 || code in 500..599
    }
}
