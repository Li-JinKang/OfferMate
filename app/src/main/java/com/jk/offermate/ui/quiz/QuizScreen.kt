package com.jk.offermate.ui.quiz

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jk.offermate.di.AppContainer
import com.jk.offermate.ui.components.PuzzleGrid
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
            .verticalScroll(rememberScrollState())
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
            return@Column
        }
        PuzzleGrid(
            count = categories.size,
            columns = 3,
            cellHeight = 112.dp,
            modifier = Modifier.padding(horizontal = 8.dp)
        ) { index, shape ->
            val c = categories[index]
            WaveFillBlob(
                progress = c.ratio,
                color = categoryColor(c.name),
                shape = shape,
                modifier = Modifier
                    .fillMaxSize()
                    .clickable { onOpenCategory(c.name) }
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(horizontal = 6.dp)
                ) {
                    Text(
                        c.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        "${c.practiced}/${c.total}",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextPrimary
                    )
                }
            }
        }
    }
}
