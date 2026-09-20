package com.jk.offermate.ui.components

import android.os.Trace

/**
 * 流式渲染链路的 system trace 打点。
 *
 * ## 为什么需要它
 *
 * 这条链路已经被归因错过一次：`inbox/2026-09-19-streaming-pace-must-be-time-not-frames.md`
 * 从 `postAndWait` 高推断"瓶颈在绘制/GPU 侧"，而后来的 trace 显示 GPU 只占 0.6~1.7ms/帧，
 * 真正的大头是 UI 线程那 14~25ms。**汇总数字不足以定位，而 Studio 的 trace 里
 * 应用代码全都塌在 `Choreographer#doFrame` 下面看不出层次。**
 *
 * 打上这些 section 之后，抓一次 trace 就能直接读出「出字的那一帧里，切块 / 解析 / 渐显
 * 各花了多少」，不必再靠频率吻合去推断。
 *
 * ## 开销
 *
 * `Trace.beginSection` 在没有抓 trace 时由 framework 内部检查 atrace tag 后直接返回，
 * 量级是几十纳秒。所以**不需要用构建变体或开关保护**，可以长期留在代码里——
 * 需要的时候抓一次就有，这比"怀疑哪里慢再加打点重新编包"快得多。
 *
 * 唯一的要求是 label 必须是**常量字符串**（不能拼接），否则每次调用都要分配。
 */
internal inline fun <T> traced(label: String, block: () -> T): T {
    Trace.beginSection(label)
    return try {
        block()
    } finally {
        Trace.endSection()
    }
}

/**
 * section 名字统一带 `om.` 前缀，方便在 trace 里筛选。
 *
 * 命名按「读 trace 时想问的问题」组织，不是按代码结构：
 * - `om.emit` 标出**哪些帧在出字**，是判断"慢帧与出字是否相关"的锚点；
 * - `om.blocks` 是每个节拍对整篇做的 sanitize + 切块；
 * - `om.annotate` / `om.fade` 是轻量路径的成本；
 * - `om.parseBlocking` 是**主线程同步解析**，出现在流式期间就说明有块没被预热。
 */
internal object TraceLabels {
    const val EMIT = "om.emit"
    const val BLOCKS = "om.blocks"
    const val ANNOTATE = "om.annotate"
    const val FADE = "om.fade"
    const val PARSE_BLOCKING = "om.parseBlocking"
}
