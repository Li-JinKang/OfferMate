package com.jk.offermate.ui.quiz

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
fun QuizRoute(container: AppContainer) {
    val viewModel: QuizViewModel = viewModel(
        factory = QuizViewModel.provideFactory(container.questionRepository)
    )
    val questions by viewModel.questions.collectAsStateWithLifecycle()
    QuizScreen(questions)
}

@Composable
fun QuizScreen(questions: List<AnsweredQuestion>) {
    if (questions.isEmpty()) {
        Box(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.Center
        ) {
            Text("题库还是空的，先在首页导入面经吧", color = TextSecondary)
        }
        return
    }
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("题库（${questions.size}）", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(bottom = 4.dp))
        }
        items(questions) { q -> AnsweredQuestionCard(q) }
    }
}
