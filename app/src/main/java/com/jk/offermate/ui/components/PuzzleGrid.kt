package com.jk.offermate.ui.components

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * 拼图网格：规则 N 列、等高，**相邻块重叠一个 tab 宽度**并采用**互补边**（一块凸→邻块凹），
 * 使凸起 tab 嵌入邻块凹口，形成无缝拼合。每个 piece 收到自身的拼图 [Shape]。
 */
@Composable
fun PuzzleGrid(
    count: Int,
    columns: Int = 2,
    cellHeight: Dp = 132.dp,
    modifier: Modifier = Modifier,
    // contentPadding：为该块内容预留的安全内边距——已避开四周 tab（body 偏移 + 凹口深度），
    // 凹边侧额外多留一个 tab 深度，防止文字探进凹口被形状裁掉。调用方直接 padding(it) 即可。
    piece: @Composable (index: Int, shape: Shape, contentPadding: PaddingValues) -> Unit
) {
    if (count == 0) return
    BoxWithConstraints(modifier) {
        val density = LocalDensity.current
        val maxWpx = with(density) { maxWidth.toPx() }
        val tab = maxWpx * 0.06f
        val cellW = (maxWpx - 2 * tab) / columns
        val cellH = with(density) { cellHeight.toPx() }
        val rows = (count + columns - 1) / columns

        // 内部边随机凸/凹（确定性）；边界为平边。
        val rightEdge = Array(rows) { r -> IntArray(columns) { c -> if (c == columns - 1) 0 else edgeRand(r, c, 1) } }
        val bottomEdge = Array(rows) { r ->
            IntArray(columns) { c ->
                val belowIndex = (r + 1) * columns + c
                if (belowIndex >= count) 0 else edgeRand(r, c, 2)
            }
        }

        // 内容安全内边距：child 比 body 四周各多出一个 tab（凸起余量），故基础内缩一个 tab
        // 落到 body 内；凹边（值<0）会向内咬掉一个 tab 深的半圆，该侧再多留一个 tab；另加少量文字呼吸留白。
        val tabDp = with(density) { tab.toDp() }
        val textMargin = 6.dp
        fun sidePad(edge: Int) = tabDp + (if (edge < 0) tabDp else 0.dp) + textMargin

        Layout(
            content = {
                for (i in 0 until count) {
                    val r = i / columns
                    val c = i % columns
                    val left = if (c == 0) 0 else -rightEdge[r][c - 1]
                    val top = if (r == 0) 0 else -bottomEdge[r - 1][c]
                    val right = rightEdge[r][c]
                    val bottom = bottomEdge[r][c]
                    val pad = PaddingValues(
                        start = sidePad(left),
                        top = sidePad(top),
                        end = sidePad(right),
                        bottom = sidePad(bottom)
                    )
                    piece(i, puzzlePiecePath(cellW, cellH, tab, top, right, bottom, left), pad)
                }
            }
        ) { measurables, _ ->
            val childW = (cellW + 2 * tab).roundToInt()
            val childH = (cellH + 2 * tab).roundToInt()
            val fixed = Constraints.fixed(childW, childH)
            val placeables = measurables.map { it.measure(fixed) }
            val boardW = (columns * cellW + 2 * tab).roundToInt()
            val boardH = (rows * cellH + 2 * tab).roundToInt()
            layout(boardW, boardH) {
                placeables.forEachIndexed { i, p ->
                    val r = i / columns
                    val c = i % columns
                    p.place((c * cellW).roundToInt(), (r * cellH).roundToInt())
                }
            }
        }
    }
}

private fun edgeRand(r: Int, c: Int, salt: Int): Int {
    val h = (r * 73856093) xor (c * 19349663) xor (salt * 83492791)
    return if (h and 1 == 0) 1 else -1
}

/**
 * 生成一块拼图路径。主体为 cellW×cellH 的矩形（四周留出 tab 余量），
 * 每条边按 top/right/bottom/left 的取值画凸(+1)/凹(-1)半圆 tab，平边(0)为直线。
 */
private fun puzzlePiecePath(
    cellW: Float,
    cellH: Float,
    tab: Float,
    top: Int,
    right: Int,
    bottom: Int,
    left: Int
): Shape = GenericShape { _, _ ->
    val l = tab
    val t = tab
    val rt = tab + cellW
    val bt = tab + cellH
    val cx = tab + cellW / 2f
    val cy = tab + cellH / 2f
    fun sweep(edge: Int) = if (edge > 0) 180f else -180f

    moveTo(l, t)
    // 上边
    if (top == 0) {
        lineTo(rt, t)
    } else {
        lineTo(cx - tab, t)
        arcTo(Rect(cx - tab, t - tab, cx + tab, t + tab), 180f, sweep(top), false)
        lineTo(rt, t)
    }
    // 右边
    if (right == 0) {
        lineTo(rt, bt)
    } else {
        lineTo(rt, cy - tab)
        arcTo(Rect(rt - tab, cy - tab, rt + tab, cy + tab), 270f, sweep(right), false)
        lineTo(rt, bt)
    }
    // 下边
    if (bottom == 0) {
        lineTo(l, bt)
    } else {
        lineTo(cx + tab, bt)
        arcTo(Rect(cx - tab, bt - tab, cx + tab, bt + tab), 0f, sweep(bottom), false)
        lineTo(l, bt)
    }
    // 左边
    if (left == 0) {
        lineTo(l, t)
    } else {
        lineTo(l, cy + tab)
        arcTo(Rect(l - tab, cy - tab, l + tab, cy + tab), 90f, sweep(left), false)
        lineTo(l, t)
    }
    close()
}
