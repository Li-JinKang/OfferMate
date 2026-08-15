package com.jk.offermate.ui.quiz

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jk.offermate.data.ai.AnsweredQuestion
import com.jk.offermate.di.AppContainer
import com.jk.offermate.ui.components.AnsweredQuestionCard
import com.jk.offermate.ui.theme.TextSecondary

@Composable
fun QuizCategoryRoute(container: AppContainer, category: String, onBack: () -> Unit) {
    val viewModel: QuizCategoryViewModel = viewModel(
        factory = QuizCategoryViewModel.provideFactory(container.questionRepository, category)
    )
    val questions by viewModel.questions.collectAsStateWithLifecycle()
    QuizCategoryScreen(category, questions, onBack, viewModel::togglePracticed)
}

@Composable
fun QuizCategoryScreen(
    category: String,
    questions: List<AnsweredQuestion>,
    onBack: () -> Unit,
    onTogglePracticed: (AnsweredQuestion) -> Unit
) {
    val practiced = questions.count { it.practiced }
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)
        ) {
            TextButton(onClick = onBack) { Text("‹ 返回") }
            Text("$category（$practiced/${questions.size} 已刷）", style = MaterialTheme.typography.titleMedium)
        }
        if (questions.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("该分类暂无题目", color = TextSecondary)
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(questions, key = { it.id.ifEmpty { it.question } }) { q ->
                    AnsweredQuestionCard(
                        q = q,
                        borderColor = categoryColor(category),
                        onTogglePracticed = { onTogglePracticed(q) }
                    )
                }
            }
        }
    }
}
