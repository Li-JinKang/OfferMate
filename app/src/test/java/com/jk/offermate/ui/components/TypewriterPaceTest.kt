package com.jk.offermate.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.roundToInt

/**
 * 出字节拍的自适应规则（`Typewriter.adaptiveEmitInterval`）。
 *
 * 守两头：**高刷屏与 60Hz 上行为不能变**（改造前是固定 32ms 目标，那是实测调好的），
 * **设备画不过来时必须退让**（否则 framesPerEmit 被压到 1，等于每帧都在出字，越慢越贪心）。
 */
class TypewriterPaceTest {

    /** 复刻 Typewriter 里的换算，用来断言「几帧出一次字」。 */
    private fun framesPerEmit(frameIntervalMs: Float): Int =
        (adaptiveEmitInterval(frameIntervalMs) / frameIntervalMs).roundToInt().coerceAtLeast(1)

    @Test
    fun `正常刷新率下沿用基准间隔`() {
        // 120Hz：8.33ms × 2 = 16.7ms 低于基准，取基准 32ms → 每 4 帧一次
        assertEquals(32f, adaptiveEmitInterval(8.33f), 0.01f)
        assertEquals(4, framesPerEmit(8.33f))

        // 90Hz
        assertEquals(32f, adaptiveEmitInterval(11.1f), 0.01f)
        assertEquals(3, framesPerEmit(11.1f))

        // 60Hz：16.67 × 2 = 33.3ms，略高于基准但仍是每 2 帧一次
        assertEquals(33.34f, adaptiveEmitInterval(16.67f), 0.05f)
        assertEquals(2, framesPerEmit(16.67f))
    }

    @Test
    fun `设备画不过来时拉长间隔`() {
        // 掉到 30fps
        assertEquals(64f, adaptiveEmitInterval(33f), 0.01f)
        // 更慢也封顶在上限，不无限拉长（观感底线）
        assertEquals(64f, adaptiveEmitInterval(40f), 0.01f)
        assertEquals(64f, adaptiveEmitInterval(100f), 0.01f)
    }

    /**
     * 核心不变式：**任何帧间隔下都不该每帧出字**。
     * 这正是改造要消除的退化情形——设备越慢越该退让，而不是把每帧都填满绘制工作。
     */
    @Test
    fun `任何帧间隔下都不会退化成每帧出字`() {
        var interval = 4f
        while (interval <= 40f) {
            assertTrue(
                "帧间隔 ${interval}ms 下退化成每帧出字",
                framesPerEmit(interval) >= 2
            )
            interval += 0.5f
        }
    }

    /** 无状态公式：帧间隔恢复后间隔自然回落，不会卡在降频档位。 */
    @Test
    fun `帧间隔恢复后间隔回落到基准`() {
        assertEquals(64f, adaptiveEmitInterval(33f), 0.01f)
        assertEquals(32f, adaptiveEmitInterval(8.33f), 0.01f)
    }
}
