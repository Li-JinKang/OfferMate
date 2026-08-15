package com.jk.offermate.ui.quiz

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jk.offermate.di.AppContainer
import com.jk.offermate.ui.components.WaveFillBlob
import com.jk.offermate.ui.theme.TextPrimary
import com.jk.offermate.ui.theme.TextSecondary

private val CategoryPalette = listOf(
    Color(0xFF5B5BE6), Color(0xFF17B26A), Color(0xFFFF2E4D),
    Color(0xFFF79009), Color(0xFF06AED4), Color(0xFF9E77ED)
)

internal fun categoryColor(category: String?): Color {
    if (category.isNullOrBlank()) return Color(0xFFB0B4C0)
    val idx = ((category.hashCode() % CategoryPalette.size) + CategoryPalette.size) % CategoryPalette.size
    return CategoryPalette[idx]
}

/**
 * 真正的拼图块形状：四条边各有一个凸/凹的半圆 tab，凹凸方向由 [seed] 的低 4 位决定。
 * 主体内缩一个 tab 半径，凸起 tab 向外延伸至组件边界内，避免被裁切。
 */
private fun puzzleShape(seed: Int): Shape = GenericShape { size, _ ->
    val w = size.width
    val h = size.height
    val r = minOf(w, h) * 0.13f
    val l = r
    val t = r
    val rt = w - r
    val bt = h - r
    val cx = w / 2f
    val cy = h / 2f
    fun bit(i: Int): Boolean = (seed shr i) and 1 == 1
    fun sweep(out: Boolean): Float = if (out) 180f else -180f

    moveTo(l, t)
    // 上边：中点凸/凹
    lineTo(cx - r, t)
    arcTo(Rect(cx - r, t - r, cx + r, t + r), 180f, sweep(bit(0)), false)
    lineTo(rt, t)
    // 右边
    lineTo(rt, cy - r)
    arcTo(Rect(rt - r, cy - r, rt + r, cy + r), 270f, sweep(bit(1)), false)
    lineTo(rt, bt)
    // 下边
    lineTo(cx + r, bt)
    arcTo(Rect(cx - r, bt - r, cx + r, bt + r), 0f, sweep(bit(2)), false)
    lineTo(l, bt)
    // 左边
    lineTo(l, cy + r)
    arcTo(Rect(l - r, cy - r, l + r, cy + r), 90f, sweep(bit(3)), false)
    lineTo(l, t)
    close()
}

private fun blobHeight(seed: Int): Dp {
    val n = ((seed % 3) + 3) % 3
    return (128 + n * 28).dp
}

@Composable
fun QuizRoute(container: AppContainer, onOpenCategory: (String) -> Unit) {
    val viewModel: QuizViewModel = viewModel(
        factory = QuizViewModel.provideFactory(container.questionRepository)
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    QuizOverviewScreen(state.categories, onOpenCategory)
}

@Composable
fun QuizOverviewScreen(categories: List<CategorySummary>, onOpenCategory: (String) -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Text(
            "题库",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 8.dp)
        )
        if (categories.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("题库还是空的，先在首页导入面经吧", color = TextSecondary)
            }
        } else {
            LazyVerticalStaggeredGrid(
                columns = StaggeredGridCells.Fixed(2),
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
                contentPadding = PaddingValues(16.dp),
                verticalItemSpacing = 12.dp,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(categories, key = { it.name }) { c ->
                    WaveFillBlob(
                        progress = c.ratio,
                        color = categoryColor(c.name),
                        shape = puzzleShape(c.name.hashCode()),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(blobHeight(c.name.hashCode()))
                            .clickable { onOpenCategory(c.name) }
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                c.name,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            Spacer(Modifier.height(4.dp))
                            Text("${c.practiced}/${c.total} 已刷", style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
                        }
                    }
                }
            }
        }
    }
}
