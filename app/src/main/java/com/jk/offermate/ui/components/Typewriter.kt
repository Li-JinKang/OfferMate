package com.jk.offermate.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.flow.first
import kotlin.math.ceil

/**
 * 双指针追赶打字机：把「已接收长度」和「已显示长度」分离，让显示长度以**帧**为节拍追赶接收长度。
 *
 * 解决的问题：SSE 到达是不均匀的（网络抖动 + provider 的 token 节奏），直接把收到的文本贴到屏幕上
 * 就是一块一块蹦字。这里插一层匀速消费，把出字节奏与网络彻底解耦。
 *
 * 附带的性能收益同样重要：无论 provider 每秒推多少 token，显示文本最多每帧变一次，
 * 也就把下游昂贵的 Markdown 重解析次数硬性封顶在帧率以内。
 *
 * 用 [withFrameNanos] 而不是 `delay(16)`：自动对齐 Choreographer 帧节拍，不与渲染抢时间，
 * 页面不可见时不会空转。
 *
 * @param fullText 当前已接收到的完整文本
 * @param isStreaming 是否正在流式生成。false 时直接全量显示——历史消息、定稿消息都不该重新"打"一遍
 * @param charsPerFrame 常规速度下每帧吐出的字符数
 */
@Composable
fun rememberTypewriterText(
    fullText: String,
    isStreaming: Boolean,
    charsPerFrame: Int = 2
): String {
    // 等价于文章里的 targetLenRef：让 tick 循环始终读到最新文本，而不是启动时捕获的快照。
    val target by rememberUpdatedState(fullText)
    var displayLen by remember { mutableIntStateOf(if (isStreaming) 0 else fullText.length) }

    LaunchedEffect(isStreaming) {
        if (!isStreaming) {
            // 流结束（或本就不是流式）：一次追平，不留尾巴。
            displayLen = target.length
            return@LaunchedEffect
        }
        // 进入流式：新一轮生成从头开始打。
        displayLen = 0
        while (true) {
            // 落后时按帧追赶。
            while (displayLen < target.length) {
                withFrameNanos { }
                val gap = target.length - displayLen
                // 自适应加速：积压越多追得越快，避免网络突发一大段时显示严重滞后。
                val step = if (gap > ACCEL_THRESHOLD) ceil(gap / ACCEL_DIVISOR).toInt() else charsPerFrame
                displayLen = (displayLen + step).coerceAtMost(target.length)
            }
            // 追平后**挂起**等下一段增量。
            // 不能写成 `withFrameNanos` 里 continue 空转：那会让 Choreographer 一直排帧，
            // token 之间的空隙（往往几十到几百毫秒）里白白唤醒渲染线程。
            snapshotFlow { target.length }.first { it > displayLen }
        }
    }

    if (displayLen >= target.length) return target
    return remember(target, displayLen) { target.takeChars(displayLen) }
}

private const val ACCEL_THRESHOLD = 50
private const val ACCEL_DIVISOR = 20f

/** 按字符截断，但不切断代理对（emoji），否则会渲染出半个字形。 */
private fun String.takeChars(n: Int): String {
    if (n >= length) return this
    var end = n.coerceAtLeast(0)
    if (end > 0 && Character.isHighSurrogate(this[end - 1])) end--
    return substring(0, end)
}
