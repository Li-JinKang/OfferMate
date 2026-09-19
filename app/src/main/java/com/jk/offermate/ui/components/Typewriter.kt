package com.jk.offermate.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
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
 * ## 两个刻意的设计
 *
 * **1）出字按帧计数，不按墙钟毫秒。** 早先是「攒够 32ms 出一次」，问题是 32ms 不是帧间隔的整数倍：
 * 60Hz 下是 1.92 帧、120Hz 下是 3.84 帧，于是实际节拍在「1 帧/2 帧」或「3 帧/4 帧」之间来回跳，
 * 观感上是轻微的不匀。改成固定每 [EMIT_EVERY_N_FRAMES] 帧出一次后节拍严格均匀，
 * 而且自动适配刷新率——60Hz 上约 33ms 一次（和原来相当），120Hz 上约 17ms 一次（更细腻）。
 * 出字**速度**仍按实测帧间隔换算，所以 `charsPerSecond` 在不同刷新率下表现一致。
 *
 * **2）返回 [State] 而不是 [String]。** 调用方常常是页面级 composable，如果直接返回 String，
 * 每个出字节拍都会让整个页面重组（重建行列表、重跑 LazyColumn 的 content lambda）。
 * 返回 State 后，读取点可以下沉到真正显示文字的那个叶子 composable，
 * 而打字机的状态仍然留在页面作用域——不会随 LazyColumn item 回收而丢失、从头再打一遍。
 * 同理 [fullText] 收的是 lambda 而非值：让调用方也能把上游的高频 state 读取推迟到这里。
 *
 * 仍然用 [withFrameNanos] 而不是 `delay`：它自带帧时间戳，且页面不可见时不会空转。
 *
 * @param fullText 取当前已接收到的完整文本（延迟读取，每帧调用）
 * @param isStreaming 是否正在流式生成。false 时直接全量显示——历史消息、定稿消息都不该重新"打"一遍
 * @param charsPerSecond 常规速度下每秒吐出的字符数
 */
@Composable
fun rememberTypewriterText(
    fullText: () -> String,
    isStreaming: Boolean,
    charsPerSecond: Int = 120
): State<String> {
    // 等价于文章里的 targetLenRef：让 tick 循环始终读到最新文本，而不是启动时捕获的快照。
    val target by rememberUpdatedState(fullText)
    // NO_LIMIT 表示"不截断"，用于非流式：避免首次组合到 LaunchedEffect 生效之间闪一帧空文本。
    val displayLen = remember { mutableIntStateOf(if (isStreaming) 0 else NO_LIMIT) }

    LaunchedEffect(isStreaming) {
        if (!isStreaming) {
            // 流结束（或本就不是流式）：一次追平，不留尾巴。
            displayLen.intValue = NO_LIMIT
            return@LaunchedEffect
        }
        // 进入流式：新一轮生成从头开始打。
        displayLen.intValue = 0
        var framesSinceEmit = 0
        var lastFrameNanos = 0L
        var frameIntervalNanos = DEFAULT_FRAME_NANOS
        while (true) {
            // 落后时按帧追赶，但只在攒够帧数的那一帧真正写 state。
            while (displayLen.intValue < target().length) {
                val now = withFrameNanos { it }
                if (lastFrameNanos != 0L) {
                    // 实测帧间隔，用来把「每 N 帧」换算成真实速度。掉帧时的异常大间隔直接丢弃，
                    // 否则会把一次卡顿的时长算进"该吐多少字"，出现突然蹦一大段。
                    val delta = now - lastFrameNanos
                    if (delta in MIN_FRAME_NANOS..MAX_FRAME_NANOS) frameIntervalNanos = delta
                }
                lastFrameNanos = now
                framesSinceEmit++
                // 帧数没攒够：这一帧什么都不改，不触发重组，几乎零成本。
                if (framesSinceEmit < EMIT_EVERY_N_FRAMES) continue

                val elapsedMs = framesSinceEmit * frameIntervalNanos / 1_000_000f
                framesSinceEmit = 0

                val gap = target().length - displayLen.intValue
                val paced = ceil(elapsedMs * charsPerSecond / 1000f).toInt()
                // 自适应加速：积压越多追得越快，避免网络突发一大段时显示严重滞后。
                val step = if (gap > ACCEL_THRESHOLD) maxOf(paced, ceil(gap / ACCEL_DIVISOR).toInt()) else paced
                displayLen.intValue =
                    (displayLen.intValue + step.coerceAtLeast(1)).coerceAtMost(target().length)
            }
            // 追平后**挂起**等下一段增量。
            // 不能写成 `withFrameNanos` 里 continue 空转：那会让 Choreographer 一直排帧，
            // token 之间的空隙（往往几十到几百毫秒）里白白唤醒渲染线程。
            // 清掉帧基准：挂起时长不该被算进「这段时间该吐多少字」，否则等待越久越会一次蹦一大段；
            // 归零同时让恢复后的第一帧立刻出字，不必再等一个间隔。
            lastFrameNanos = 0L
            framesSinceEmit = 0
            snapshotFlow { target().length }.first { it > displayLen.intValue }
        }
    }

    // derivedStateOf 让「截断后的文本」只在真正被读取的地方建立订阅，且自带结果相等性检查：
    // 同一帧内多次读取只算一次，displayLen 变化但截断结果不变时也不会通知下游。
    return remember {
        derivedStateOf {
            val text = target()
            val len = displayLen.intValue
            if (len >= text.length) text else text.takeChars(len)
        }
    }
}

/**
 * 出字的帧间隔。2 帧 ≈ 60Hz 上 33ms、120Hz 上 17ms：观感连续，又把下游重解析/重排的次数
 * 压到刷新率的一半。
 */
private const val EMIT_EVERY_N_FRAMES = 2

/** 表示"不截断，直接显示全文"。 */
private const val NO_LIMIT = Int.MAX_VALUE

/** 帧间隔的合理区间（约 250Hz ~ 25Hz）与初值（按 60Hz）。超出区间视为掉帧，不用来估算速度。 */
private const val MIN_FRAME_NANOS = 4_000_000L
private const val MAX_FRAME_NANOS = 40_000_000L
private const val DEFAULT_FRAME_NANOS = 16_666_667L

private const val ACCEL_THRESHOLD = 50
private const val ACCEL_DIVISOR = 8f

/** 按字符截断，但不切断代理对（emoji），否则会渲染出半个字形。 */
private fun String.takeChars(n: Int): String {
    if (n >= length) return this
    var end = n.coerceAtLeast(0)
    if (end > 0 && Character.isHighSurrogate(this[end - 1])) end--
    return substring(0, end)
}
