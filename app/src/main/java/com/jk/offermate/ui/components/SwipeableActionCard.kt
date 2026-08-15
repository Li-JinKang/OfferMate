package com.jk.offermate.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.jk.offermate.ui.theme.Indigo
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * 左划露出"置顶/删除"操作的卡片容器。向左拖动露出右侧按钮，超过一半吸附展开，否则回弹。
 */
@Composable
fun SwipeableActionCard(
    pinned: Boolean,
    onTogglePin: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val actionWidth = 76.dp
    val maxOffsetPx = with(density) { (actionWidth * 2).toPx() }
    val offsetX = remember { Animatable(0f) }

    Box(modifier.fillMaxWidth()) {
        Row(Modifier.matchParentSize(), horizontalArrangement = Arrangement.End) {
            ActionCell(if (pinned) "取消置顶" else "置顶", Indigo, actionWidth) {
                scope.launch { offsetX.animateTo(0f) }
                onTogglePin()
            }
            ActionCell("删除", Color(0xFFE5484D), actionWidth) {
                scope.launch { offsetX.animateTo(0f) }
                onDelete()
            }
        }
        Box(
            Modifier
                .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                .fillMaxWidth()
                .draggable(
                    orientation = Orientation.Horizontal,
                    state = rememberDraggableState { delta ->
                        scope.launch { offsetX.snapTo((offsetX.value + delta).coerceIn(-maxOffsetPx, 0f)) }
                    },
                    onDragStopped = {
                        val target = if (offsetX.value < -maxOffsetPx / 2) -maxOffsetPx else 0f
                        scope.launch { offsetX.animateTo(target) }
                    }
                )
        ) {
            content()
        }
    }
}

@Composable
private fun ActionCell(label: String, bg: Color, width: Dp, onClick: () -> Unit) {
    Box(
        Modifier
            .fillMaxHeight()
            .width(width)
            .background(bg)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = Color.White, style = MaterialTheme.typography.labelLarge)
    }
}
