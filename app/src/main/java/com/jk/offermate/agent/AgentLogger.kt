package com.jk.offermate.agent

import android.util.Log

/**
 * Agent / AI 调用链路的日志端口。做成可注入接口而非直接调 [Log]，是为了让纯逻辑类
 * （[ToolCallingAgent] 等）在 JVM 单测里用 [NoopAgentLogger]，不触发 `android.util.Log` 未 mock 的问题。
 *
 * 消息用 lambda 延迟构造：关闭日志（Noop）时不产生字符串拼接开销。
 */
interface AgentLogger {
    fun d(message: () -> String)
    // error 放前面、message 放最后，便于尾随 lambda 调用：logger.w { "msg" } 或 logger.w(e) { "msg" }
    fun w(error: Throwable? = null, message: () -> String)

    companion object {
        /** 统一日志 TAG，`adb logcat -s OfferMateAI` 即可过滤。 */
        const val TAG = "OfferMateAI"

        /** 内容日志截断，避免刷屏与隐私全量落盘。 */
        fun brief(text: String, max: Int = 200): String {
            val oneLine = text.replace('\n', ' ').trim()
            return if (oneLine.length <= max) oneLine else oneLine.take(max) + "…(${oneLine.length})"
        }
    }
}

/** 生产实现：输出到 Logcat。 */
object AndroidAgentLogger : AgentLogger {
    override fun d(message: () -> String) {
        Log.d(AgentLogger.TAG, message())
    }

    override fun w(error: Throwable?, message: () -> String) {
        Log.w(AgentLogger.TAG, message(), error)
    }
}

/** 空实现：单测/无需日志时使用。 */
object NoopAgentLogger : AgentLogger {
    override fun d(message: () -> String) {}
    override fun w(error: Throwable?, message: () -> String) {}
}
