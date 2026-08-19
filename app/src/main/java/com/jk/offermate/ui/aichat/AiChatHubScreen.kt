package com.jk.offermate.ui.aichat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jk.offermate.data.local.PostMappers
import com.jk.offermate.di.AppContainer
import com.jk.offermate.ui.followup.FollowUpScreen
import com.jk.offermate.ui.quiz.categoryColor
import com.jk.offermate.ui.theme.TextPrimary
import com.jk.offermate.ui.theme.TextSecondary
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiChatRoute(
    container: AppContainer,
    currentDestination: androidx.navigation.NavDestination? = null,
    onNavigateTab: (com.jk.offermate.ui.navigation.Screen) -> Unit = {},
    /** 由“追问”携带进来的题目 id：进入后自动就该题打开/新建对话。 */
    pendingQuestionId: String? = null,
    onPendingConsumed: () -> Unit = {}
) {
    // dock 状态提升到此：离开页面（点其它 Tab）时先归位为 true，让 Tab 胶囊向下滑到对齐位再退出
    var inputInFront by remember { mutableStateOf(true) }

    // dock 里用的 Tab 胶囊：不透明、与输入胶囊等高
    val tabs: @Composable () -> Unit = {
        com.jk.offermate.ui.navigation.TabPill(
            currentDestination = currentDestination,
            onNavigate = { screen ->
                // 归位：Tab 胶囊从前置(高)滑回对齐位，与退出淡出同步
                inputInFront = true
                onNavigateTab(screen)
            },
            containerColor = MaterialTheme.colorScheme.surface,
            shadowElevation = 8.dp,
            barHeight = 56.dp
        )
    }
    val viewModel: AiChatViewModel = viewModel(
        factory = AiChatViewModel.provideFactory(
            container.questionRepository,
            container.conversationRepository
        )
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    // 当前会话选择。默认 null/null = 全新空白对话（固定页面，不依赖抽屉数据、不转圈）。
    // 会话记录在首条消息发送时才懒创建，避免留下空会话。
    var activeQuestionId by rememberSaveable { mutableStateOf<String?>(null) }
    var activeConversationId by rememberSaveable { mutableStateOf<String?>(null) }
    // 用于区分多次“新对话”，强制重建 ChatViewModel。
    var newChatToken by rememberSaveable { mutableStateOf(0) }

    // 新建一段空白自由对话（不落库，直接切到空白页）
    val startFreeChat: () -> Unit = {
        activeQuestionId = null
        activeConversationId = null
        newChatToken++
        inputInFront = true
    }

    // “追问”跳转：就该题打开/复用会话并置为当前
    LaunchedEffect(pendingQuestionId) {
        val qId = pendingQuestionId ?: return@LaunchedEffect
        val q = container.questionRepository.observeById(qId).first()
        val convId = container.conversationRepository.getOrCreateForQuestion(qId, q?.question.orEmpty())
        activeQuestionId = qId
        activeConversationId = convId
        onPendingConsumed()
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(Modifier.fillMaxHeight()) {
                AiChatDrawer(
                    state = state,
                    onQueryChange = viewModel::onQueryChange,
                    onNewChat = {
                        startFreeChat()
                        scope.launch { drawerState.close() }
                    },
                    onResume = { item ->
                        activeQuestionId = item.questionId
                        activeConversationId = item.conversationId
                        scope.launch { drawerState.close() }
                    },
                    onStart = { candidate ->
                        scope.launch {
                            val convId = container.conversationRepository
                                .getOrCreateForQuestion(candidate.id, candidate.question)
                            activeQuestionId = candidate.id
                            activeConversationId = convId
                            drawerState.close()
                        }
                    }
                )
            }
        }
    ) {
        // 固定页面：始终渲染对话页（空白或指定会话），不依赖抽屉数据、不转圈
        AiChatConversation(
            container = container,
            conversationId = activeConversationId,
            questionId = activeQuestionId,
            newChatToken = newChatToken,
            onOpenDrawer = { scope.launch { drawerState.open() } },
            onNewChat = startFreeChat,
            tabs = tabs,
            inputInFront = inputInFront,
            onInputInFrontChange = { inputInFront = it }
        )
    }
}

/** 内嵌的对话本体：以会话为中心（题目可选），复用 FollowUpScreen，返回箭头换成抽屉菜单。 */
@Composable
private fun AiChatConversation(
    container: AppContainer,
    conversationId: String?,
    questionId: String?,
    newChatToken: Int,
    onOpenDrawer: () -> Unit,
    onNewChat: () -> Unit,
    tabs: @Composable () -> Unit,
    inputInFront: Boolean,
    onInputInFrontChange: (Boolean) -> Unit
) {
    val viewModel: ChatViewModel = viewModel(
        // conversationId 为空的新对话用 token 区分，保证每次“新对话”是全新的 VM
        key = "chat:${conversationId ?: "new"}:$questionId:$newChatToken",
        factory = ChatViewModel.provideFactory(
            initialConversationId = conversationId,
            questionId = questionId,
            questionRepository = container.questionRepository,
            conversationRepository = container.conversationRepository,
            followUpService = container.followUpService,
            resumeRepository = container.resumeRepository
        )
    )
    val question by viewModel.question.collectAsStateWithLifecycle()
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val sending by viewModel.sending.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val notice by viewModel.notice.collectAsStateWithLifecycle()

    FollowUpScreen(
        question = question,
        // 会话历史/切换交给抽屉，这里不用页内会话切换器
        conversations = emptyList(),
        activeConversationId = null,
        messages = messages,
        sending = sending,
        error = error,
        notice = notice,
        onBack = {},
        onSend = viewModel::send,
        onUpdateAnswer = viewModel::updateAnswerFromDiscussion,
        onNewSession = onNewChat,
        onSwitchSession = {},
        onConsumeError = viewModel::consumeError,
        onConsumeNotice = viewModel::consumeNotice,
        onOpenDrawer = onOpenDrawer,
        bottomTabs = tabs,
        dockInputInFront = inputInFront,
        onDockInputInFrontChange = onInputInFrontChange
    )
}

@Composable
private fun AiChatDrawer(
    state: AiChatDrawerState,
    onQueryChange: (String) -> Unit,
    onNewChat: () -> Unit,
    onResume: (ConversationHistoryItem) -> Unit,
    onStart: (StartCandidate) -> Unit
) {
    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 12.dp)
    ) {
        Text(
            "AI 对话",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 4.dp, top = 12.dp, bottom = 10.dp)
        )
        com.jk.offermate.ui.components.SearchField(
            value = state.query,
            onValueChange = onQueryChange,
            placeholder = "搜索对话 / 题目"
        )
        Spacer(Modifier.height(8.dp))

        // 新建自由对话
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.fillMaxWidth().clickable(onClick = onNewChat)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp)
            ) {
                Icon(
                    Icons.Filled.Add,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "新对话",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
        Spacer(Modifier.height(8.dp))

        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (state.history.isNotEmpty()) {
                item { DrawerSectionLabel("对话历史") }
                items(state.history, key = { it.conversationId }) { item ->
                    DrawerHistoryRow(item = item, onClick = { onResume(item) })
                }
                item { Spacer(Modifier.height(8.dp)) }
            }

            item { DrawerSectionLabel("追问题目") }
            if (state.candidates.isEmpty()) {
                item {
                    Text(
                        "没有匹配的题目",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            } else {
                items(state.candidates, key = { it.id }) { candidate ->
                    DrawerCandidateRow(candidate = candidate, onClick = { onStart(candidate) })
                }
            }
        }
    }
}

@Composable
private fun DrawerSectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = TextSecondary,
        modifier = Modifier.padding(start = 4.dp, top = 4.dp, bottom = 2.dp)
    )
}

@Composable
private fun DrawerHistoryRow(item: ConversationHistoryItem, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                item.title,
                style = MaterialTheme.typography.bodyLarge,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            Text(
                PostMappers.relativeTimeLabel(item.updatedAt, System.currentTimeMillis()),
                style = MaterialTheme.typography.labelMedium,
                color = TextSecondary
            )
        }
    }
}

@Composable
private fun DrawerCandidateRow(candidate: StartCandidate, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(categoryColor(candidate.category))
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                candidate.question,
                style = MaterialTheme.typography.bodyMedium,
                color = TextPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            Text(candidate.category, style = MaterialTheme.typography.labelMedium, color = TextSecondary)
        }
        Spacer(Modifier.width(8.dp))
        Icon(Icons.Filled.Add, contentDescription = "开始对话", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
    }
}
