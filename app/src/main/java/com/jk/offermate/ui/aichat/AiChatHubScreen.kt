package com.jk.offermate.ui.aichat

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
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

/** AI 对话历史抽屉宽度（比 Material3 默认 360dp 更窄）。 */
private val DrawerWidth = 300.dp

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
    /** 上报常驻 dock 的目标透明度（随抽屉手势进度变化，1=完全显示，0=完全隐藏）。 */
    onDockAlpha: (Float) -> Unit = {},
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

    // 抽屉打开进度（0=关闭，1=打开），随手势实时变化。用它精确驱动常驻 dock（输入胶囊 + Tab 胶囊）
    // 的淡入淡出：右划抽屉逐渐展开时 dock 逐渐隐去，左划回收时逐渐现形。
    val density = LocalDensity.current
    val drawerWidthPx = with(density) { DrawerWidth.toPx() }
    LaunchedEffect(drawerState, drawerWidthPx, onDockAlpha) {
        snapshotFlow {
            val off = drawerState.currentOffset
            if (off.isNaN()) {
                if (drawerState.isOpen) 1f else 0f
            } else {
                // 关闭时 offset = -宽度，打开时 offset = 0 → 进度 = 1 + offset/宽度
                (1f + off / drawerWidthPx).coerceIn(0f, 1f)
            }
        }.collect { progress -> onDockAlpha(1f - progress) }
    }
    // 离开对话页时恢复 dock 不透明，避免残留淡出状态。
    DisposableEffect(Unit) {
        onDispose { onDockAlpha(1f) }
    }

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
            ModalDrawerSheet(Modifier.fillMaxHeight().width(DrawerWidth)) {
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
                    onDelete = { item ->
                        // 删除的是当前会话则回到空白新对话
                        if (item.conversationId == activeConversationId) startFreeChat()
                        viewModel.deleteConversation(item.conversationId)
                    },
                    onTogglePin = { item ->
                        viewModel.setPinned(item.conversationId, !item.pinned)
                    },
                    onRename = { item, newTitle ->
                        viewModel.rename(item.conversationId, newTitle)
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
            followUpService = container.followUpService
        )
    )
    val question by viewModel.question.collectAsStateWithLifecycle()
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val title by viewModel.title.collectAsStateWithLifecycle()
    val sending by viewModel.sending.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val notice by viewModel.notice.collectAsStateWithLifecycle()
    val canUpdateAnswer by viewModel.canUpdateAnswer.collectAsStateWithLifecycle()

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
        titleOverride = title,
        // 会话历史/切换交给抽屉，这里不用页内会话切换器
        conversations = emptyList(),
        activeConversationId = null,
        messages = messages,
        sending = sending,
        error = error,
        notice = notice,
        onBack = {},
        onUpdateAnswer = viewModel::updateAnswerFromDiscussion,
        canUpdateAnswer = canUpdateAnswer,
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
    onDelete: (ConversationHistoryItem) -> Unit,
    onTogglePin: (ConversationHistoryItem) -> Unit,
    onRename: (ConversationHistoryItem, String) -> Unit
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
            if (state.history.isEmpty()) {
                item {
                    Text(
                        if (state.query.isBlank()) "暂无对话历史" else "没有匹配的对话",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            } else {
                items(state.history, key = { it.conversationId }) { item ->
                    DrawerHistoryRow(
                        item = item,
                        query = state.query,
                        onClick = { onResume(item) },
                        onDelete = { onDelete(item) },
                        onTogglePin = { onTogglePin(item) },
                        onRename = { newTitle -> onRename(item, newTitle) }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DrawerHistoryRow(
    item: ConversationHistoryItem,
    query: String,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onTogglePin: () -> Unit,
    onRename: (String) -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    // Box 作为 Popup 的锚点：其边界即整行边界，用于让选择框右侧与选项右侧对齐。
    Box(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .combinedClickable(onClick = onClick, onLongClick = { menuOpen = true })
                .padding(horizontal = 10.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 置顶标记
            if (item.pinned) {
                Icon(
                    Icons.Filled.KeyboardArrowUp,
                    contentDescription = "已置顶",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(4.dp))
            }
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

        if (menuOpen) {
            HistoryItemMenu(
                pinned = item.pinned,
                onDismiss = { menuOpen = false },
                onTogglePin = {
                    menuOpen = false
                    onTogglePin()
                },
                onRename = {
                    menuOpen = false
                    renaming = true
                },
                onDelete = {
                    menuOpen = false
                    onDelete()
                }
            )
        }
    }

    if (renaming) {
        RenameDialog(
            initial = item.title,
            onDismiss = { renaming = false },
            onConfirm = { newTitle ->
                renaming = false
                onRename(newTitle)
            }
        )
    }
}

/**
 * 长按历史选项弹出的圆角选择框：默认显示在选项**上方**、与选项**右侧对齐**；
 * 上方空间不足时改为显示在下方。含删除 / 置顶 / 重命名三个选项。
 */
@Composable
private fun HistoryItemMenu(
    pinned: Boolean,
    onDismiss: () -> Unit,
    onTogglePin: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    val gapPx = with(LocalDensity.current) { 6.dp.roundToPx() }
    val positionProvider = remember(gapPx) { RightAlignedAboveOrBelow(gapPx) }
    Popup(
        popupPositionProvider = positionProvider,
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true)
    ) {
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 8.dp,
            tonalElevation = 2.dp
        ) {
            Column(Modifier.width(160.dp).padding(vertical = 4.dp)) {
                HistoryMenuItem(
                    icon = Icons.Filled.KeyboardArrowUp,
                    label = if (pinned) "取消置顶" else "置顶",
                    onClick = onTogglePin
                )
                HistoryMenuItem(
                    icon = Icons.Filled.Edit,
                    label = "重命名",
                    onClick = onRename
                )
                HistoryMenuItem(
                    icon = Icons.Filled.Delete,
                    label = "删除对话",
                    tint = MaterialTheme.colorScheme.error,
                    onClick = onDelete
                )
            }
        }
    }
}

@Composable
private fun HistoryMenuItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    tint: androidx.compose.ui.graphics.Color = TextPrimary,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium, color = tint)
    }
}

/** 重命名对话的对话框。 */
@Composable
private fun RenameDialog(
    initial: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("重命名对话") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                placeholder = { Text("输入新的标题") }
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(text) },
                enabled = text.isNotBlank()
            ) { Text("确定") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

/**
 * 弹层定位：右侧与锚点（历史选项）右侧对齐；优先置于锚点上方，
 * 上方空间不足（越界）时置于下方。
 */
private class RightAlignedAboveOrBelow(private val gap: Int) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize
    ): IntOffset {
        val x = (anchorBounds.right - popupContentSize.width).coerceIn(0, (windowSize.width - popupContentSize.width).coerceAtLeast(0))
        val above = anchorBounds.top - popupContentSize.height - gap
        val y = if (above >= 0) above else anchorBounds.bottom + gap
        return IntOffset(x, y)
    }
}


