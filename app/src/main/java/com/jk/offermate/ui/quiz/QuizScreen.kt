package com.jk.offermate.ui.quiz

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jk.offermate.di.AppContainer
import com.jk.offermate.ui.components.AnsweredQuestionCard
import com.jk.offermate.ui.theme.OutlineSoft
import com.jk.offermate.ui.theme.TextSecondary

private val CategoryPalette = listOf(
    Color(0xFF5B5BE6), Color(0xFF17B26A), Color(0xFFFF2E4D),
    Color(0xFFF79009), Color(0xFF06AED4), Color(0xFF9E77ED)
)

private fun categoryColor(category: String?): Color {
    if (category.isNullOrBlank()) return Color(0xFFB0B4C0)
    val idx = ((category.hashCode() % CategoryPalette.size) + CategoryPalette.size) % CategoryPalette.size
    return CategoryPalette[idx]
}

@Composable
fun QuizRoute(container: AppContainer) {
    val viewModel: QuizViewModel = viewModel(
        factory = QuizViewModel.provideFactory(container.questionRepository)
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    QuizScreen(state, viewModel::onSelect)
}

@Composable
fun QuizScreen(state: QuizUiState, onSelect: (String) -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Text(
            "题库（${state.questions.size}）",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 8.dp)
        )
        CategoryChips(state.categories, state.selected, onSelect)

        if (state.questions.isEmpty()) {
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
                items(state.questions, key = { it.question }) { q ->
                    AnsweredQuestionCard(q = q, borderColor = categoryColor(q.tags.firstOrNull()))
                }
            }
        }
    }
}

@Composable
private fun CategoryChips(categories: List<String>, selected: String, onSelect: (String) -> Unit) {
    Row(
        Modifier
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        categories.forEach { category ->
            val isSelected = category == selected
            val accent = if (category == QuizUiState.ALL) MaterialTheme.colorScheme.primary else categoryColor(category)
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = if (isSelected) accent else MaterialTheme.colorScheme.surface,
                border = if (isSelected) null else BorderStroke(1.dp, OutlineSoft),
                modifier = Modifier.clickable { onSelect(category) }
            ) {
                Text(
                    text = category,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
            Spacer(Modifier.size(10.dp))
        }
    }
}
