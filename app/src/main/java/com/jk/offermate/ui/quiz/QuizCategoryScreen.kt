package com.jk.offermate.ui.quiz

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jk.offermate.agent.AnsweredQuestion
import com.jk.offermate.di.AppContainer
import com.jk.offermate.ui.components.AnsweredQuestionCard
import com.jk.offermate.ui.components.SwipeableActionCard
import com.jk.offermate.ui.theme.TextSecondary

@Composable
fun QuizCategoryRoute(
    container: AppContainer,
    category: String,
    onBack: () -> Unit,
    highlightQuestionId: String? = null
) {
    val viewModel: QuizCategoryViewModel = viewModel(
        factory = QuizCategoryViewModel.provideFactory(
            container.questionRepository,
            container.categoryRepository,
            category
        )
    )
    val questions by viewModel.questions.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    QuizCategoryScreen(
        category = category,
        questions = questions,
        categories = categories,
        onBack = onBack,
        onTogglePracticed = viewModel::togglePracticed,
        onDelete = viewModel::deleteQuestion,
        onChangeCategory = viewModel::changeCategory,
        highlightQuestionId = highlightQuestionId
    )
}

@Composable
fun QuizCategoryScreen(
    category: String,
    questions: List<AnsweredQuestion>,
    categories: List<String> = emptyList(),
    onBack: () -> Unit,
    onTogglePracticed: (AnsweredQuestion) -> Unit,
    onDelete: (AnsweredQuestion) -> Unit = {},
    onChangeCategory: (AnsweredQuestion, String) -> Unit = { _, _ -> },
    highlightQuestionId: String? = null
) {
    val practiced = questions.count { it.practiced }
    val listState = rememberLazyListState()
    // 待移动分类的题目（非空时弹出选择分类对话框）
    var moveTarget by remember { mutableStateOf<AnsweredQuestion?>(null) }

    // 从搜索结果进入时：数据就绪后滚动到目标题并触发该卡片的提示动画。
    // 只消费一次，避免后续列表更新（如标记已刷）时重复滚动/脉冲。
    var pulseId by remember { mutableStateOf<String?>(null) }
    var highlightConsumed by remember { mutableStateOf(false) }
    LaunchedEffect(highlightQuestionId, questions) {
        val target = highlightQuestionId ?: return@LaunchedEffect
        if (highlightConsumed) return@LaunchedEffect
        val index = questions.indexOfFirst { it.id == target }
        if (index >= 0) {
            highlightConsumed = true
            listState.animateScrollToItem(index)
            pulseId = target
        }
    }

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
                state = listState,
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(questions, key = { it.id.ifEmpty { it.question } }) { q ->
                    // 所有题目均支持左滑删除；删除入口不再放卡片内
                    SwipeableActionCard(onDelete = { onDelete(q) }) {
                        AnsweredQuestionCard(
                            q = q,
                            borderColor = categoryColor(category),
                            onTogglePracticed = { onTogglePracticed(q) },
                            onChangeCategory = { moveTarget = q },
                            pulse = q.id.isNotEmpty() && q.id == pulseId
                        )
                    }
                }
            }
        }
    }

    moveTarget?.let { target ->
        MoveCategoryDialog(
            current = CategoryResolver.displayCategory(target),
            categories = categories,
            onConfirm = { name ->
                onChangeCategory(target, name)
                moveTarget = null
            },
            onDismiss = { moveTarget = null }
        )
    }
}

/** 移动分类对话框：可从已有分类中选，或输入新分类名。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MoveCategoryDialog(
    current: String,
    categories: List<String>,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var custom by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf<String?>(null) }
    val target = custom.trim().ifEmpty { selected.orEmpty() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("移动到分类") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("当前：$current", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                val options = categories.filter { it != current }
                if (options.isNotEmpty()) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        options.forEach { name ->
                            FilterChip(
                                selected = custom.isBlank() && selected == name,
                                onClick = {
                                    selected = name
                                    custom = ""
                                },
                                label = { Text(name) }
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = custom,
                    onValueChange = {
                        custom = it
                        if (it.isNotBlank()) selected = null
                    },
                    label = { Text("或新建分类") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(enabled = target.isNotBlank() && target != current, onClick = { onConfirm(target) }) {
                Text("移动")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}
