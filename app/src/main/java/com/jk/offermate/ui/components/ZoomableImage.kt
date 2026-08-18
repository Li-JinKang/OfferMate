package com.jk.offermate.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollDispatcher
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.jk.offermate.ui.theme.CardSurface

private const val MIN_SCALE = 1f
private const val MAX_SCALE = 5f

/** 本组件不消费父级滚动，仅用其 [NestedScrollDispatcher] 把手势"退回"给外层可滚动容器。 */
private val PassThroughNestedScrollConnection = object : NestedScrollConnection {}

/**
 * 可双指缩放/平移的图片。缩放范围 [MIN_SCALE]~[MAX_SCALE]；缩回 1x 时归位。
 *
 * 平移距离限制在"内容溢出容器"的范围内（不能无限拖走），超出边界的剩余位移会通过
 * [NestedScrollDispatcher] 转发给外层可滚动容器（如简历页的 `verticalScroll`），
 * 拖到边缘后可以继续带动外层滚动，而不是卡死在图片内部。
 */
@Composable
fun ZoomableImage(
    image: ImageBitmap,
    modifier: Modifier = Modifier,
    height: Dp = 420.dp
) {
    var scale by remember { mutableFloatStateOf(MIN_SCALE) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var containerSize by remember { mutableStateOf(IntSize.Zero) }
    val scrollDispatcher = remember { NestedScrollDispatcher() }

    Image(
        bitmap = image,
        contentDescription = "简历预览",
        contentScale = ContentScale.Fit,
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .background(CardSurface)
            .clipToBounds()
            .onSizeChanged { containerSize = it }
            .nestedScroll(connection = PassThroughNestedScrollConnection, dispatcher = scrollDispatcher)
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    val isPinch = zoom != 1f
                    val newScale = (scale * zoom).coerceIn(MIN_SCALE, MAX_SCALE)
                    scale = newScale

                    if (newScale <= MIN_SCALE) {
                        // 未放大：不占用手势，单指拖动整段转发给外层滚动容器
                        offset = Offset.Zero
                        if (!isPinch && pan != Offset.Zero) {
                            scrollDispatcher.dispatchPostScroll(
                                consumed = Offset.Zero,
                                available = pan,
                                source = androidx.compose.ui.input.nestedscroll.NestedScrollSource.UserInput
                            )
                        }
                        return@detectTransformGestures
                    }

                    // 放大后：内容溢出容器的一半即为可平移的边界，防止无限拖走
                    val maxOffsetX = (containerSize.width * (newScale - 1f)) / 2f
                    val maxOffsetY = (containerSize.height * (newScale - 1f)) / 2f
                    val desired = offset + pan
                    val clamped = Offset(
                        desired.x.coerceIn(-maxOffsetX, maxOffsetX),
                        desired.y.coerceIn(-maxOffsetY, maxOffsetY)
                    )
                    val consumed = clamped - offset
                    val leftover = pan - consumed
                    offset = clamped

                    // 已经拖到边界、还有剩余位移时，交给外层容器继续拖动
                    if (!isPinch && leftover != Offset.Zero) {
                        scrollDispatcher.dispatchPostScroll(
                            consumed = consumed,
                            available = leftover,
                            source = androidx.compose.ui.input.nestedscroll.NestedScrollSource.UserInput
                        )
                    }
                }
            }
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationX = offset.x
                translationY = offset.y
            }
    )
}
