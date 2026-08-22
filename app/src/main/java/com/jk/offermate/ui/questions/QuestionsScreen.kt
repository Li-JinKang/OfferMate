package com.jk.offermate.ui.questions

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Email
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jk.offermate.R
import com.jk.offermate.agent.AnsweredQuestion
import com.jk.offermate.di.AppContainer
import com.jk.offermate.ui.components.ActionBarItemSpec
import com.jk.offermate.ui.components.ActionDeleteColor
import com.jk.offermate.ui.components.ActionPracticeColor
import com.jk.offermate.ui.components.AnsweredQuestionCard
import com.jk.offermate.ui.components.FloatingActionBar
import com.jk.offermate.ui.quiz.categoryColor
import com.jk.offermate.ui.theme.Indigo
import com.jk.offermate.ui.theme.TextSecondary

/** 题目页的批量操作模式：进入后卡片显示对应透明图标，点击卡片即对该题执行动作。 */
private enum class QuestionActionMode { None, Delete, Practice, FollowUp }

@Composable
fun QuestionsRoute(
    container: AppContainer,
    postId: String,
    onBack: () -> Unit,
    onFollowUp: (String) -> Unit = {}
) {
    val viewModel: QuestionsViewModel = viewModel(
        factory = QuestionsViewModel.provideFactory(container.questionRepository, postId)
    )
    val questions by viewModel.questions.collectAsStateWithLifecycle()
    QuestionsScreen(
        questions = questions,
        onBack = onBack,
        onTogglePracticed = viewModel::togglePracticed,
        onDelete = viewModel::deleteQuestion,
        onFollowUp = onFollowUp
    )
}

@Composable
fun QuestionsScreen(
    questions: List<AnsweredQuestion>,
    onBack: () -> Unit,
    onTogglePracticed: (AnsweredQuestion) -> Unit,
    onDelete: (AnsweredQuestion) -> Unit = {},
    onFollowUp: (String) -> Unit = {}
) {
    var mode by remember { mutableStateOf(QuestionActionMode.None) }
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val barReserve = navBottom + 80.dp

    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        if (questions.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("分析中或暂无题目", color = TextSecondary)
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = statusTop + 12.dp,
                    bottom = barReserve
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Text(
                        "题目 · ${questions.size}",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
                items(questions, key = { it.id.ifEmpty { it.question } }) { q ->
                    val actionIcon: ImageVector?
                    val actionTint: Color
                    val onAction: (() -> Unit)?
                    when (mode) {
                        QuestionActionMode.Delete -> {
                            actionIcon = Icons.Filled.Close; actionTint = ActionDeleteColor; onAction = { onDelete(q) }
                        }
                        QuestionActionMode.Practice -> {
                            actionIcon = Icons.Filled.Check; actionTint = ActionPracticeColor; onAction = { onTogglePracticed(q) }
                        }
                        QuestionActionMode.FollowUp -> {
                            actionIcon = Icons.Filled.Email
                            actionTint = Indigo
                            onAction = q.id.takeIf { it.isNotBlank() }?.let { id -> { onFollowUp(id) } }
                        }
                        QuestionActionMode.None -> {
                            actionIcon = null; actionTint = Color.Unspecified; onAction = null
                        }
                    }
                    AnsweredQuestionCard(
                        q = q,
                        borderColor = categoryColor(q.tags.firstOrNull()),
                        actionIcon = actionIcon,
                        actionTint = actionTint,
                        onActionClick = onAction
                    )
                }
            }
        }

        val selectMode = { m: QuestionActionMode -> mode = if (mode == m) QuestionActionMode.None else m }
        FloatingActionBar(
            items = listOf(
                ActionBarItemSpec(
                    icon = null,
                    label = "返回",
                    selected = false,
                    activeColor = MaterialTheme.colorScheme.primary,
                    iconRes = R.drawable.ic_reply_back,
                    onClick = onBack
                ),
                ActionBarItemSpec(
                    icon = null,
                    label = "删除",
                    selected = mode == QuestionActionMode.Delete,
                    activeColor = ActionDeleteColor,
                    iconRes = R.drawable.ic_delete_bin,
                    coloredIcon = true
                ) { selectMode(QuestionActionMode.Delete) },
                ActionBarItemSpec(
                    icon = null,
                    label = "已刷",
                    selected = mode == QuestionActionMode.Practice,
                    activeColor = ActionPracticeColor,
                    iconRes = R.drawable.ic_check
                ) { selectMode(QuestionActionMode.Practice) },
                ActionBarItemSpec(Icons.Filled.Email, "追问", mode == QuestionActionMode.FollowUp, Indigo) { selectMode(QuestionActionMode.FollowUp) }
            ),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 12.dp)
        )
    }
}
