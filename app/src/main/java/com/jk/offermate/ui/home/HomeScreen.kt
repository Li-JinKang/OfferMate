package com.jk.offermate.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jk.offermate.di.AppContainer
import com.jk.offermate.domain.model.Platform
import com.jk.offermate.domain.model.Post
import com.jk.offermate.domain.model.PostBadge
import com.jk.offermate.ui.theme.BadgeHotBg
import com.jk.offermate.ui.theme.BadgeHotText
import com.jk.offermate.ui.theme.BadgeMatchBg
import com.jk.offermate.ui.theme.BadgeMatchText
import com.jk.offermate.ui.theme.NowcoderGreen
import com.jk.offermate.ui.theme.OutlineSoft
import com.jk.offermate.ui.theme.TextSecondary
import com.jk.offermate.ui.theme.XiaohongshuRed

/**
 * 首页入口：从容器取 ViewModel（工厂注入 Repository），把状态与事件桥接到无状态的 [HomeScreen]。
 */
@Composable
fun HomeRoute(container: AppContainer) {
    val viewModel: HomeViewModel = viewModel(
        factory = HomeViewModel.provideFactory(container.postRepository)
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    HomeScreen(
        state = state,
        onLinkChange = viewModel::onLinkChange,
        onExtract = viewModel::onExtract,
        onSelectFilter = viewModel::onSelectFilter,
        onOpenPost = { /* TODO: 导航到题目列表 */ }
    )
}

/** 无状态首页，便于预览与测试。 */
@Composable
fun HomeScreen(
    state: HomeUiState,
    onLinkChange: (String) -> Unit,
    onExtract: () -> Unit,
    onSelectFilter: (String) -> Unit,
    onOpenPost: (Post) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            ImportCard(
                linkInput = state.linkInput,
                isExtracting = state.isExtracting,
                onLinkChange = onLinkChange,
                onExtract = onExtract
            )
        }
        item {
            SourceFilterRow(
                filters = state.filters,
                selected = state.selectedFilter,
                onSelect = onSelectFilter
            )
        }
        items(state.posts, key = { it.id }) { post ->
            PostCard(post = post, onOpen = { onOpenPost(post) })
        }
    }
}

@Composable
private fun ImportCard(
    linkInput: String,
    isExtracting: Boolean,
    onLinkChange: (String) -> Unit,
    onExtract: () -> Unit
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
                    placeholder = { Text("粘贴牛客/小红书面经链接…") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.size(8.dp))
                Button(
                    onClick = onExtract,
                    enabled = linkInput.isNotBlank() && !isExtracting,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(if (isExtracting) "解析中…" else "提取")
                }
            }
        }
    }
}

@Composable
private fun SourceFilterRow(
    filters: List<String>,
    selected: String,
    onSelect: (String) -> Unit
) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
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
            Spacer(Modifier.height(6.dp))
            Text(
                post.summary,
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
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
