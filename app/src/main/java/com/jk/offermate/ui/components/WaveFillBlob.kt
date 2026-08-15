package com.jk.offermate.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import kotlin.math.PI
import kotlin.math.sin

/**
 * 波浪水填充碎片：在 [shape] 形状内，用双层正弦波表示 [progress]（0~1）的填充高度，
 * 相位随时间平移产生波澜流动。未填充部分用浅色底。
 */
@Composable
fun WaveFillBlob(
    progress: Float,
    color: Color,
    shape: Shape,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val transition = rememberInfiniteTransition(label = "wave")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(2200, easing = LinearEasing), RepeatMode.Restart),
        label = "phase"
    )

    Box(
        modifier
            .clip(shape)
            .background(color.copy(alpha = 0.12f)),
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val p = progress.coerceIn(0f, 1f)
            if (p <= 0f) return@Canvas
            val level = h * (1f - p)
            val amp = (h * 0.04f).coerceAtLeast(4f)

            fun wave(offset: Float, alpha: Float) {
                val path = Path().apply {
                    moveTo(0f, level)
                    var x = 0f
                    val step = 6f
                    while (x <= w) {
                        val angle = (x / w) * 2f * PI.toFloat() + phase + offset
                        val y = level + amp * sin(angle.toDouble()).toFloat()
                        lineTo(x, y)
                        x += step
                    }
                    lineTo(w, h)
                    lineTo(0f, h)
                    close()
                }
                drawPath(path, color.copy(alpha = alpha))
            }

            wave(PI.toFloat(), 0.35f) // 后层
            wave(0f, 0.9f)            // 前层
        }
        content()
    }
}
