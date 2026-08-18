package com.jk.offermate.ui.quiz

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jk.offermate.agent.Difficulty
import com.jk.offermate.di.AppContainer
import com.jk.offermate.ui.components.PuzzleGrid
import com.jk.offermate.ui.components.SearchField
import com.jk.offermate.ui.components.WaveFillBlob
import com.jk.offermate.ui.theme.OutlineSoft
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
        factory = QuizViewModel.provideFactory(container.questionRepository, container.categoryRepository)
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    QuizOverviewScreen(
        state = state,
        onQueryChange = viewModel::onQueryChange,
        onOpenCategory = onOpenCategory,
        onAddCategory = viewModel::addCategory,
        onAddQuestion = viewModel::addManualQuestion
    )
}

@Composable
fun QuizOverviewScreen(
    state: QuizOverviewState,
    onQueryChange: (String) -> Unit,
    onOpenCategory: (String) -> Unit,
    onAddCategory: (String) -> Unit,
    onAddQuestion: (String, String, String, Difficulty) -> Unit
) {
    var showAddCategory by remember { mutableStateOf(false) }
    var showAddQuestion by remember { mutableStateOf(false) }
    val categories = state.categories

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
    ) {
        QuizHeader(
            query = state.query,
            onQueryChange = onQueryChange,
            onAddCategory = { showAddCategory = true },
            onAddQuestion = { showAddQuestion = true }
        )

        when {
            // 搜索态：展示命中题目列表
            state.searching -> {
                if (state.results.isEmpty()) {
                    Box(Modifier.fillMaxWidth().padding(top = 48.dp), contentAlignment = Alignment.Center) {
                        Text("没有匹配的题目", color = TextSecondary)
                    }
                } else {
                    Column(
                        Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        state.results.forEach { q ->
                            val cat = CategoryResolver.displayCategory(q)
                            SearchResultRow(question = q.question, category = cat) { onOpenCategory(cat) }
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }

            categories.isEmpty() -> {
                Box(Modifier.fillMaxSize().padding(top = 48.dp), contentAlignment = Alignment.Center) {
                    Text("题库还是空的，导入面经或手动新增题目吧", color = TextSecondary)
                }
            }

            else -> {
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
                            // 内边距避开拼图四周的凸/凹 tab，防止文字被切掉
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 18.dp, vertical = 14.dp)
                        ) {
                            Text(
                                c.name,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center
                            )
                            Spacer(Modifier.height(2.dp))
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
    }

    if (showAddCategory) {
        AddCategoryDialog(
            onConfirm = { name -> onAddCategory(name); showAddCategory = false },
            onDismiss = { showAddCategory = false }
        )
    }
    if (showAddQuestion) {
        AddQuestionDialog(
            existingCategories = categories.map { it.name },
            onConfirm = { q, a, cat, diff -> onAddQuestion(q, a, cat, diff); showAddQuestion = false },
            onDismiss = { showAddQuestion = false }
        )
    }
}

@Composable
private fun SearchResultRow(question: String, category: String, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, OutlineSoft),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            Box(
                Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(categoryColor(category))
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    question,
                    style = MaterialTheme.typography.bodyLarge,
                    color = TextPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Text(category, style = MaterialTheme.typography.labelMedium, color = TextSecondary)
            }
        }
    }
}

/**
 * 题库页头部：状态栏留白 + 搜索框 + 新增分类/题目 圆角按钮。
 */
@Composable
private fun QuizHeader(
    query: String,
    onQueryChange: (String) -> Unit,
    onAddCategory: () -> Unit,
    onAddQuestion: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 16.dp)
            .padding(top = 12.dp, bottom = 4.dp)
    ) {
        SearchField(
            value = query,
            onValueChange = onQueryChange,
            placeholder = "搜索题目 / 分类"
        )

        Spacer(Modifier.height(12.dp))

        // 新增分类 / 新增题目 圆角按钮
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            HeaderActionButton(
                text = "新增分类",
                icon = Icons.AutoMirrored.Filled.List,
                onClick = onAddCategory,
                modifier = Modifier.weight(1f)
            )
            HeaderActionButton(
                text = "新增题目",
                icon = Icons.Filled.Add,
                onClick = onAddQuestion,
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(Modifier.height(4.dp))
    }
}

@Composable
private fun HeaderActionButton(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, OutlineSoft),
        modifier = modifier.clickable(onClick = onClick)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp)
        ) {
            Icon(
                icon,
                contentDescription = text,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary
            )
        }
    }
}

@Composable
private fun AddCategoryDialog(onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("新增分类") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("分类名称（如：Kotlin、网络）") },
                singleLine = true
            )
        },
        confirmButton = { TextButton(enabled = name.isNotBlank(), onClick = { onConfirm(name) }) { Text("添加") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

@Composable
private fun AddQuestionDialog(
    existingCategories: List<String>,
    onConfirm: (String, String, String, Difficulty) -> Unit,
    onDismiss: () -> Unit
) {
    var question by remember { mutableStateOf("") }
    var answer by remember { mutableStateOf("") }
    var category by remember { mutableStateOf(existingCategories.firstOrNull().orEmpty()) }
    var difficulty by remember { mutableStateOf(Difficulty.MEDIUM) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("新增题目") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(question, { question = it }, label = { Text("题目") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(answer, { answer = it }, label = { Text("参考答案（可选，支持 Markdown）") }, minLines = 2, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(category, { category = it }, label = { Text("分类") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                if (existingCategories.isNotEmpty()) {
                    Row(Modifier.horizontalScroll(rememberScrollState())) {
                        existingCategories.forEach { c ->
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.surface,
                                border = androidx.compose.foundation.BorderStroke(1.dp, OutlineSoft),
                                modifier = Modifier.clickable { category = c }
                            ) {
                                Text(c, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp))
                            }
                            Spacer(Modifier.width(6.dp))
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("难度：", style = MaterialTheme.typography.bodyMedium)
                    listOf(Difficulty.EASY to "简单", Difficulty.MEDIUM to "中等", Difficulty.HARD to "困难").forEach { (d, label) ->
                        val selected = difficulty == d
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                            border = if (selected) null else androidx.compose.foundation.BorderStroke(1.dp, OutlineSoft),
                            modifier = Modifier.clickable { difficulty = d }
                        ) {
                            Text(
                                label,
                                style = MaterialTheme.typography.labelMedium,
                                color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                        Spacer(Modifier.width(6.dp))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = question.isNotBlank() && category.isNotBlank(),
                onClick = { onConfirm(question, answer, category, difficulty) }
            ) { Text("添加") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}
