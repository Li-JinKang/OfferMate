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
 * 附带的性能收益同样重要：无论 provider 每秒推多少 token，出字节奏都由这里说了算，
 * 也就把下游昂贵的 Markdown 重解析 + 重排次数握在手里。
 *
 * 这个节奏**不是每帧一次**。显示长度每变一次，尾块就要重解析、重组、重新测量一遍；
 * 60Hz 下这笔开销直接吃满主线程（实测一帧 doFrame 能到 50ms 上下，recompose + traversal 各占一半）。
 * 而文本流本身 30 次/秒的更新在观感上已经完全连续，所以这里改为**按时间间隔**出字：
 * 每 [MIN_EMIT_INTERVAL_MS] 毫秒吐一次，一次吐够这段时间对应的字符数，速度不变、更新次数减半以上。
 *
 * 仍然用 [withFrameNanos] 而不是 `delay`：它自带的帧时间戳正好用来算间隔，且页面不可见时不会空转。
 *
 * @param fullText 当前已接收到的完整文本
 * @param isStreaming 是否正在流式生成。false 时直接全量显示——历史消息、定稿消息都不该重新"打"一遍
 * @param charsPerSecond 常规速度下每秒吐出的字符数
 */
@Composable
fun rememberTypewriterText(
    fullText: String,
    isStreaming: Boolean,
    charsPerSecond: Int = 120
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
        var lastEmitNanos = 0L
        while (true) {
            // 落后时按帧追赶，但只在攒够时间间隔的那一帧真正写 state。
            while (displayLen < target.length) {
                val now = withFrameNanos { it }
                val sinceLastEmit = now - lastEmitNanos
                // 间隔没到：这一帧什么都不改，不触发重组，几乎零成本。
                if (lastEmitNanos != 0L && sinceLastEmit < MIN_EMIT_INTERVAL_MS * 1_000_000L) continue
                val elapsedMs = if (lastEmitNanos == 0L) {
                    MIN_EMIT_INTERVAL_MS.toFloat()
                } else {
                    sinceLastEmit / 1_000_000f
                }
                lastEmitNanos = now

                val gap = target.length - displayLen
                val paced = ceil(elapsedMs * charsPerSecond / 1000f).toInt()
                // 自适应加速：积压越多追得越快，避免网络突发一大段时显示严重滞后。
                val step = if (gap > ACCEL_THRESHOLD) maxOf(paced, ceil(gap / ACCEL_DIVISOR).toInt()) else paced
                displayLen = (displayLen + step.coerceAtLeast(1)).coerceAtMost(target.length)
            }
            // 追平后**挂起**等下一段增量。
            // 不能写成 `withFrameNanos` 里 continue 空转：那会让 Choreographer 一直排帧，
            // token 之间的空隙（往往几十到几百毫秒）里白白唤醒渲染线程。
            // 清掉时间基准：挂起时长不该被算进「这段时间该吐多少字」，否则等待越久越会一次蹦一大段；
            // 归零同时让恢复后的第一帧立刻出字，不必再等一个间隔。
            lastEmitNanos = 0L
            snapshotFlow { target.length }.first { it > displayLen }
        }
    }

    if (displayLen >= target.length) return target
    return remember(target, displayLen) { target.takeChars(displayLen) }
}

/** 出字的最小间隔。~30 次/秒：观感上连续，又把下游重解析/重排的次数砍到帧率的一半。 */
private const val MIN_EMIT_INTERVAL_MS = 32

private const val ACCEL_THRESHOLD = 50
private const val ACCEL_DIVISOR = 8f

/** 按字符截断，但不切断代理对（emoji），否则会渲染出半个字形。 */
private fun String.takeChars(n: Int): String {
    if (n >= length) return this
    var end = n.coerceAtLeast(0)
    if (end > 0 && Character.isHighSurrogate(this[end - 1])) end--
    return substring(0, end)
}
