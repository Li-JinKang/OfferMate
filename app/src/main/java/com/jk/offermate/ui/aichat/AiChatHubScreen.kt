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
import com.jk.offermate.ui.followup.FollowUpViewModel
import com.jk.offermate.ui.quiz.categoryColor
import com.jk.offermate.ui.theme.TextPrimary
import com.jk.offermate.ui.theme.TextSecondary
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiChatRoute(
    container: AppContainer,
    /** 由“追问”携带进来的题目 id：进入后自动就该题打开/新建对话。 */
    pendingQuestionId: String? = null,
    onPendingConsumed: () -> Unit = {}
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

    // 当前展示的会话（跨题目）；进入 Tab 时默认取最近一次对话
    var activeQuestionId by rememberSaveable { mutableStateOf<String?>(null) }
    var activeConversationId by rememberSaveable { mutableStateOf<String?>(null) }
    var seeded by rememberSaveable { mutableStateOf(false) }

    // “追问”跳转：就该题打开/新建会话并置为当前
    LaunchedEffect(pendingQuestionId) {
        val qId = pendingQuestionId ?: return@LaunchedEffect
        val q = container.questionRepository.observeById(qId).first()
        val convId = container.conversationRepository.getOrCreateForQuestion(qId, q?.question.orEmpty())
        activeQuestionId = qId
        activeConversationId = convId
        seeded = true
        onPendingConsumed()
    }

    LaunchedEffect(state.latest) {
        if (!seeded && activeConversationId == null) {
            state.latest?.let {
                activeQuestionId = it.questionId
                activeConversationId = it.conversationId
                seeded = true
            }
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(Modifier.fillMaxHeight()) {
                AiChatDrawer(
                    state = state,
                    onQueryChange = viewModel::onQueryChange,
                    onResume = { item ->
                        activeQuestionId = item.questionId
                        activeConversationId = item.conversationId
                        seeded = true
                        scope.launch { drawerState.close() }
                    },
                    onStart = { candidate ->
                        scope.launch {
                            val convId = container.conversationRepository
                                .getOrCreateForQuestion(candidate.id, candidate.question)
                            activeQuestionId = candidate.id
                            activeConversationId = convId
                            seeded = true
                            drawerState.close()
                        }
                    }
                )
            }
        }
    ) {
        val qId = activeQuestionId
        val cId = activeConversationId
        if (qId == null || cId == null) {
            EmptyChatState(onOpenDrawer = { scope.launch { drawerState.open() } })
        } else {
            AiChatConversation(
                container = container,
                questionId = qId,
                conversationId = cId,
                onOpenDrawer = { scope.launch { drawerState.open() } }
            )
        }
    }
}

/** 内嵌的对话本体：复用 FollowUpViewModel/FollowUpScreen，仅把返回箭头换成抽屉菜单。 */
@Composable
private fun AiChatConversation(
    container: AppContainer,
    questionId: String,
    conversationId: String,
    onOpenDrawer: () -> Unit
) {
    val viewModel: FollowUpViewModel = viewModel(
        key = "aichat:$questionId:$conversationId",
        factory = FollowUpViewModel.provideFactory(
            questionId = questionId,
            questionRepository = container.questionRepository,
            conversationRepository = container.conversationRepository,
            followUpService = container.followUpService,
            resumeRepository = container.resumeRepository,
            initialConversationId = conversationId
        )
    )
    val question by viewModel.question.collectAsStateWithLifecycle()
    val conversations by viewModel.conversations.collectAsStateWithLifecycle()
    val activeConversationId by viewModel.activeConversationId.collectAsStateWithLifecycle()
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val sending by viewModel.sending.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val notice by viewModel.notice.collectAsStateWithLifecycle()

    FollowUpScreen(
        question = question,
        conversations = conversations,
        activeConversationId = activeConversationId,
        messages = messages,
        sending = sending,
        error = error,
        notice = notice,
        onBack = {},
        onSend = viewModel::send,
        onUpdateAnswer = viewModel::updateAnswerFromDiscussion,
        onNewSession = viewModel::startNewSession,
        onSwitchSession = viewModel::switchToConversation,
        onConsumeError = viewModel::consumeError,
        onConsumeNotice = viewModel::consumeNotice,
        onOpenDrawer = onOpenDrawer
    )
}

@Composable
private fun EmptyChatState(onOpenDrawer: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onOpenDrawer) {
                Icon(Icons.Filled.Menu, contentDescription = "对话历史", tint = TextPrimary)
            }
            Text("AI 对话", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
        }
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(32.dp)
            ) {
                Text(
                    "还没有对话",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "从左侧选择一道题，就它向 AI 追问",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
                Spacer(Modifier.height(20.dp))
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable(onClick = onOpenDrawer)
                ) {
                    Text(
                        "选择题目",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun AiChatDrawer(
    state: AiChatDrawerState,
    onQueryChange: (String) -> Unit,
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

            item { DrawerSectionLabel("开始新对话") }
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
