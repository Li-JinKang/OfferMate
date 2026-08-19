package com.jk.offermate.ui.quiz

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jk.offermate.agent.AnsweredQuestion
import com.jk.offermate.di.AppContainer
import com.jk.offermate.ui.components.AnsweredQuestionCard
import com.jk.offermate.ui.theme.Indigo
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

/** 分类页的批量操作模式：进入某模式后，卡片显示对应透明图标，点击卡片即对该题执行动作。 */
private enum class CategoryActionMode { None, Delete, Practice, Move }

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
    // 当前批量操作模式；再次点击同一按钮退出
    var mode by remember { mutableStateOf(CategoryActionMode.None) }

    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    // 为底部浮动胶囊预留空间：导航栏 + 胶囊高度(56) + 上下留白
    val barReserve = navBottom + 80.dp

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
            listState.animateScrollToItem(index + 1) // +1 跳过标题头部
            pulseId = target
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        if (questions.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("该分类暂无题目", color = TextSecondary)
            }
        } else {
            LazyColumn(
                state = listState,
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
                        "$category · $practiced/${questions.size} 已刷",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
                items(questions, key = { it.id.ifEmpty { it.question } }) { q ->
                    // 按当前模式决定卡片中央的透明图标与点击动作
                    val actionIcon: ImageVector?
                    val actionTint: Color
                    val onAction: (() -> Unit)?
                    when (mode) {
                        CategoryActionMode.Delete -> {
                            actionIcon = Icons.Filled.Close; actionTint = DeleteRed; onAction = { onDelete(q) }
                        }
                        CategoryActionMode.Practice -> {
                            actionIcon = Icons.Filled.Check; actionTint = PracticeGreen; onAction = { onTogglePracticed(q) }
                        }
                        CategoryActionMode.Move -> {
                            actionIcon = Icons.AutoMirrored.Filled.ArrowForward; actionTint = Indigo; onAction = { moveTarget = q }
                        }
                        CategoryActionMode.None -> {
                            actionIcon = null; actionTint = Color.Unspecified; onAction = null
                        }
                    }
                    AnsweredQuestionCard(
                        q = q,
                        borderColor = categoryColor(category),
                        pulse = q.id.isNotEmpty() && q.id == pulseId,
                        actionIcon = actionIcon,
                        actionTint = actionTint,
                        onActionClick = onAction
                    )
                }
            }
        }

        CategoryActionBar(
            mode = mode,
            onBack = onBack,
            onSelectMode = { m -> mode = if (mode == m) CategoryActionMode.None else m },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 12.dp)
        )
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

/** 底部浮动操作胶囊：返回 + 三个可切换的批量操作模式（删除/已刷/移动），仿首页 Tab 胶囊样式。 */
@Composable
private fun CategoryActionBar(
    mode: CategoryActionMode,
    onBack: () -> Unit,
    onSelectMode: (CategoryActionMode) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
        shadowElevation = 12.dp,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            ActionBarItem(Icons.AutoMirrored.Filled.ArrowBack, "返回", false, MaterialTheme.colorScheme.primary, onBack)
            ActionBarItem(Icons.Filled.Delete, "删除", mode == CategoryActionMode.Delete, DeleteRed) { onSelectMode(CategoryActionMode.Delete) }
            ActionBarItem(Icons.Filled.Check, "已刷", mode == CategoryActionMode.Practice, PracticeGreen) { onSelectMode(CategoryActionMode.Practice) }
            ActionBarItem(Icons.AutoMirrored.Filled.ArrowForward, "移动", mode == CategoryActionMode.Move, Indigo) { onSelectMode(CategoryActionMode.Move) }
        }
    }
}

@Composable
private fun ActionBarItem(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    activeColor: Color,
    onClick: () -> Unit
) {
    val bg = if (selected) activeColor.copy(alpha = 0.15f) else Color.Transparent
    val content = if (selected) activeColor else MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .height(40.dp)
            .padding(horizontal = if (selected) 14.dp else 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Icon(icon, contentDescription = label, tint = content)
        if (selected) {
            Spacer(Modifier.width(6.dp))
            Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = content)
        }
    }
}

private val DeleteRed = Color(0xFFE5484D)
private val PracticeGreen = Color(0xFF17B26A)

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
