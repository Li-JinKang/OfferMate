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
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jk.offermate.data.local.PostMappers
import com.jk.offermate.di.AppContainer
import com.jk.offermate.ui.followup.CompactChatInput
import com.jk.offermate.ui.followup.FollowUpScreen
import com.jk.offermate.ui.quiz.categoryColor
import com.jk.offermate.ui.theme.TextPrimary
import com.jk.offermate.ui.components.highlightMatches
import com.jk.offermate.ui.theme.TextSecondary
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiChatRoute(
    container: AppContainer,
    /** 由“追问”携带进来的题目 id：进入后自动就该题打开/新建对话。 */
    pendingQuestionId: String? = null,
    onPendingConsumed: () -> Unit = {},
    /** 把当前会话的输入胶囊内容登记到 app 级常驻 dock；离开页面时以 null 注销。 */
    registerInputContent: ((@Composable () -> Unit)?) -> Unit = {},
    /** 请求把 dock 的输入胶囊归位到前台（新对话/切换会话时用）。 */
    onResetInputFront: () -> Unit = {},
    /** 内容底部留白：为悬浮 dock 预留空间。 */
    contentBottomPadding: Dp = 0.dp
) {
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
    // 从搜索结果进入时要定位到的命中消息 id（一次性）。
    var pendingScrollMessageId by rememberSaveable { mutableStateOf<Long?>(null) }

    // 新建一段空白自由对话（不落库，直接切到空白页）
    val startFreeChat: () -> Unit = {
        activeQuestionId = null
        activeConversationId = null
        newChatToken++
        onResetInputFront()
    }

    // “追问”跳转：就该题打开/复用会话并置为当前
    LaunchedEffect(pendingQuestionId) {
        val qId = pendingQuestionId ?: return@LaunchedEffect
        val q = container.questionRepository.observeById(qId).first()
        val convId = container.conversationRepository.getOrCreateForQuestion(qId, q?.question.orEmpty())
        activeQuestionId = qId
        activeConversationId = convId
        onResetInputFront()
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
                        // 命中消息则进入后定位到该条（片段搜索结果）
                        pendingScrollMessageId = item.hitMessageId
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
            registerInputContent = registerInputContent,
            contentBottomPadding = contentBottomPadding,
            scrollToMessageId = pendingScrollMessageId,
            onScrollConsumed = { pendingScrollMessageId = null }
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
    registerInputContent: ((@Composable () -> Unit)?) -> Unit,
    contentBottomPadding: Dp = 0.dp,
    scrollToMessageId: Long? = null,
    onScrollConsumed: () -> Unit = {}
) {
    // conversationId 为空的新对话用 token 区分，保证每次“新对话”是全新的 VM
    val chatKey = "chat:${conversationId ?: "new"}:$questionId:$newChatToken"
    val viewModel: ChatViewModel = viewModel(
        key = chatKey,
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

    // 输入内容随会话（chatKey）切换而重置
    var input by rememberSaveable(chatKey) { mutableStateOf("") }

    // 搜索命中消息 id → 列表下标（用 COUNT 查询，避免把消息 id 带入领域模型）
    var scrollToIndex by remember(conversationId, scrollToMessageId) { mutableStateOf<Int?>(null) }
    LaunchedEffect(conversationId, scrollToMessageId) {
        scrollToIndex = if (scrollToMessageId != null && conversationId != null) {
            container.conversationRepository.messageIndex(conversationId, scrollToMessageId)
        } else {
            null
        }
    }

    // 把输入胶囊内容登记到 app 级常驻 dock；会话切换时替换、离开对话页时注销。
    // 该 lambda 在 dock 的组合作用域中执行，会订阅 input / sending 状态，输入即时更新。
    DisposableEffect(chatKey) {
        registerInputContent {
            CompactChatInput(
                input = input,
                onInputChange = { input = it },
                sending = sending,
                onSend = {
                    viewModel.send(input)
                    input = ""
                }
            )
        }
        onDispose { registerInputContent(null) }
    }

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
        onUpdateAnswer = viewModel::updateAnswerFromDiscussion,
        onNewSession = onNewChat,
        onSwitchSession = {},
        onConsumeError = viewModel::consumeError,
        onConsumeNotice = viewModel::consumeNotice,
        onOpenDrawer = onOpenDrawer,
        contentBottomPadding = contentBottomPadding,
        scrollToIndex = scrollToIndex,
        onScrollConsumed = onScrollConsumed
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
                    DrawerHistoryRow(item = item, query = state.query, onClick = { onResume(item) })
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
private fun DrawerHistoryRow(item: ConversationHistoryItem, query: String, onClick: () -> Unit) {
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
                highlightMatches(item.title, query, MaterialTheme.colorScheme.primary),
                style = MaterialTheme.typography.bodyLarge,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            // 命中消息内容时展示片段（关键词高亮）
            item.snippet?.let { snippet ->
                Spacer(Modifier.height(2.dp))
                Text(
                    highlightMatches(snippet, query, MaterialTheme.colorScheme.primary),
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
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
