package com.jk.offermate.ui.home

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jk.offermate.di.AppContainer
import com.jk.offermate.domain.model.Platform
import com.jk.offermate.domain.model.Post
import com.jk.offermate.domain.model.PostBadge
import com.jk.offermate.ui.components.SwipeableActionCard
import com.jk.offermate.ui.theme.BadgeHotBg
import com.jk.offermate.ui.theme.BadgeHotText
import com.jk.offermate.ui.theme.BadgeMatchBg
import com.jk.offermate.ui.theme.BadgeMatchText
import com.jk.offermate.ui.theme.NowcoderGreen
import com.jk.offermate.ui.theme.OutlineSoft
import com.jk.offermate.ui.theme.TextSecondary
import com.jk.offermate.ui.theme.XiaohongshuRed

@Composable
fun HomeRoute(
    container: AppContainer,
    onOpenPost: (String) -> Unit,
    onOpenSettings: () -> Unit = {},
    sharedText: String? = null,
    onSharedTextConsumed: () -> Unit = {}
) {
    val viewModel: HomeViewModel = viewModel(
        factory = HomeViewModel.provideFactory(
            postRepository = container.postRepository,
            importScheduler = container.importScheduler,
            resumeRepository = container.resumeRepository,
            settingsRepository = container.settingsRepository
        )
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    // 一次性 Toast（如：未上传简历仍继续分析）
    val context = LocalContext.current
    LaunchedEffect(state.toast) {
        state.toast?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            viewModel.onConsumeToast()
        }
    }

    // 从其他 App 分享进来的文本：收到后立即入队分析，并通知上层清空，避免重进/旋转时重复触发。
    LaunchedEffect(sharedText) {
        if (!sharedText.isNullOrBlank()) {
            viewModel.onSharedTextReceived(sharedText)
            onSharedTextConsumed()
        }
    }

    HomeScreen(
        state = state,
        onLinkChange = viewModel::onLinkChange,
        onExtract = viewModel::onExtract,
        onSelectFilter = viewModel::onSelectFilter,
        onToggleManualPaste = viewModel::onToggleManualPaste,
        onPasteAnalyze = viewModel::onPasteAnalyze,
        onOpenPost = { post -> onOpenPost(post.id) },
        onOpenSettings = onOpenSettings,
        onTogglePin = viewModel::onTogglePin,
        onDelete = viewModel::onDelete
    )
}

@Composable
fun HomeScreen(
    state: HomeUiState,
    onLinkChange: (String) -> Unit,
    onExtract: () -> Unit,
    onSelectFilter: (String) -> Unit,
    onToggleManualPaste: () -> Unit,
    onPasteAnalyze: (String) -> Unit,
    onOpenPost: (Post) -> Unit,
    onOpenSettings: () -> Unit,
    onTogglePin: (Post) -> Unit,
    onDelete: (String) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            HomeHeader(
                postCount = state.posts.size,
                questionCount = state.posts.sumOf { it.parsedQuestionCount },
                onOpenSettings = onOpenSettings
            )
        }

        item {
            ImportCard(
                linkInput = state.linkInput,
                onLinkChange = onLinkChange,
                onExtract = onExtract,
                onToggleManualPaste = onToggleManualPaste
            )
        }

        state.message?.let { msg ->
            item { MessageBanner(msg) }
        }

        if (state.manualPasteVisible) {
            item { ManualPasteCard(onAnalyze = onPasteAnalyze) }
        }

        item {
            SourceFilterRow(
                filters = state.filters,
                selected = state.selectedFilter,
                onSelect = onSelectFilter
            )
        }

        if (state.posts.isEmpty()) {
            item { EmptyHint() }
        } else {
            items(state.posts, key = { it.id }) { post ->
                SwipeableActionCard(
                    pinned = post.pinned,
                    onTogglePin = { onTogglePin(post) },
                    onDelete = { onDelete(post.id) }
                ) {
                    PostCard(post = post, onOpen = { onOpenPost(post) })
                }
            }
        }
    }
}

/**
 * 首页头部：主标题「面经」+ 一行统计副标题 + 右侧圆形搜索按钮（搜索暂为占位）。
 */
@Composable
private fun HomeHeader(
    postCount: Int,
    questionCount: Int,
    onOpenSettings: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                "面经",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = if (postCount == 0) {
                    "粘贴链接，开始收录你的第一篇面经"
                } else {
                    "已收录 $postCount 篇 · 解析 $questionCount 题"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )
        }
        Spacer(Modifier.size(12.dp))
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surface)
                .clickable(onClick = onOpenSettings),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Filled.Settings,
                contentDescription = "设置",
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun ImportCard(
    linkInput: String,
    onLinkChange: (String) -> Unit,
    onExtract: () -> Unit,
    onToggleManualPaste: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "一键解析面经帖子",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                PillTag("牛客", BadgeHotText, BadgeHotBg)
                Spacer(Modifier.size(8.dp))
                PillTag("小红书", XiaohongshuRed, Color(0x14FF2E4D))
            }
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = linkInput,
                    onValueChange = onLinkChange,
                    placeholder = { Text("粘贴面经链接…") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.size(8.dp))
                Button(
                    onClick = onExtract,
                    enabled = linkInput.isNotBlank(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("提取")
                }
            }
            TextButton(onClick = onToggleManualPaste) {
                Text("读取失败？手动粘贴正文")
            }
        }
    }
}

@Composable
private fun MessageBanner(message: String) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.primaryContainer
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        )
    }
}

@Composable
private fun ManualPasteCard(onAnalyze: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("手动粘贴正文", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                placeholder = { Text("把帖子正文粘贴到这里…") },
                minLines = 4,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                Button(onClick = { onAnalyze(text) }, enabled = text.isNotBlank()) {
                    Text("分析粘贴内容")
                }
            }
        }
    }
}

@Composable
private fun EmptyHint() {
    Box(Modifier.fillMaxWidth().padding(top = 40.dp), contentAlignment = Alignment.Center) {
        Text("还没有记录，粘贴一个面经链接开始吧", color = TextSecondary)
    }
}

@Composable
private fun SourceFilterRow(
    filters: List<String>,
    selected: String,
    onSelect: (String) -> Unit
) {
    if (filters.size <= 1) return
    androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        items(filters, key = { it }) { filter ->
            val isSelected = filter == selected
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                border = if (isSelected) null else BorderStroke(1.dp, OutlineSoft),
                modifier = Modifier.clickable { onSelect(filter) }
            ) {
                Text(
                    text = filter,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun PostCard(post: Post, onOpen: () -> Unit) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.clickable(onClick = onOpen)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (post.pinned) {
                    PillTag("置顶", BadgeMatchText, BadgeMatchBg)
                    Spacer(Modifier.size(6.dp))
                }
                Box(
                    Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(post.platform.dotColor())
                )
                Spacer(Modifier.size(6.dp))
                Text(post.platform.displayName(), style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                Spacer(Modifier.size(6.dp))
                Text("· ${post.timeLabel}", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                Spacer(Modifier.weight(1f))
                post.badge?.let { BadgeView(it) }
            }
            Spacer(Modifier.height(10.dp))
            Text(post.title, style = MaterialTheme.typography.titleMedium)
            if (post.summary.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    post.summary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "已智能解析 ${post.parsedQuestionCount} 道核心题",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    "去刷此篇 ›",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun BadgeView(badge: PostBadge) {
    when (badge) {
        is PostBadge.ResumeMatch -> PillTag("与简历匹配 ${badge.percent}%", BadgeMatchText, BadgeMatchBg)
        is PostBadge.Label -> PillTag(badge.text, BadgeHotText, BadgeHotBg)
    }
}

@Composable
private fun PillTag(text: String, contentColor: Color, containerColor: Color) {
    Surface(shape = RoundedCornerShape(8.dp), color = containerColor) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = contentColor,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

private fun Platform.displayName(): String = when (this) {
    Platform.NOWCODER -> "牛客网"
    Platform.XIAOHONGSHU -> "小红书"
}

private fun Platform.dotColor(): Color = when (this) {
    Platform.NOWCODER -> NowcoderGreen
    Platform.XIAOHONGSHU -> XiaohongshuRed
}
