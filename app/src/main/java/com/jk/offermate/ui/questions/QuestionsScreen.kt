package com.jk.offermate.ui.questions

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jk.offermate.data.ai.AnsweredQuestion
import com.jk.offermate.di.AppContainer
import com.jk.offermate.ui.theme.BadgeMatchBg
import com.jk.offermate.ui.theme.BadgeMatchText
import com.jk.offermate.ui.theme.TextSecondary

@Composable
fun QuestionsRoute(container: AppContainer, postId: String, onBack: () -> Unit) {
    val viewModel: QuestionsViewModel = viewModel(
        factory = QuestionsViewModel.provideFactory(container.questionRepository, postId)
    )
    val questions by viewModel.questions.collectAsStateWithLifecycle()
    QuestionsScreen(questions = questions, onBack = onBack)
}

@Composable
fun QuestionsScreen(questions: List<AnsweredQuestion>, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)) {
            TextButton(onClick = onBack) { Text("‹ 返回") }
            Text("题目（${questions.size}）", style = MaterialTheme.typography.titleMedium)
        }
        if (questions.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("分析中或暂无题目", color = TextSecondary)
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(questions) { q -> QuestionCard(q) }
            }
        }
    }
}

@Composable
private fun QuestionCard(q: AnsweredQuestion) {
    var revealed by remember { mutableStateOf(false) }
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (q.tags.isNotEmpty()) {
                    Text(q.tags.joinToString(" · "), style = MaterialTheme.typography.bodyMedium, color = TextSecondary, modifier = Modifier.weight(1f))
                } else {
                    Spacer(Modifier.weight(1f))
                }
                Surface(shape = RoundedCornerShape(8.dp), color = BadgeMatchBg) {
                    Text(
                        "相关 ${q.relevanceScore}",
                        style = MaterialTheme.typography.labelLarge,
                        color = BadgeMatchText,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(q.question, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(12.dp))
            if (revealed) {
                Text(q.answer, style = MaterialTheme.typography.bodyMedium)
                if (q.keyPoints.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text("要点：" + q.keyPoints.joinToString("；"), style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                }
            } else {
                OutlinedButton(onClick = { revealed = true }) { Text("显示答案") }
            }
        }
    }
}
