package com.jk.offermate.ui.followup

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.jk.offermate.agent.pipeline.AnsweredQuestion
import com.jk.offermate.agent.ChatMessage
import com.jk.offermate.agent.Role
import com.jk.offermate.data.local.entity.ConversationEntity
import com.jk.offermate.ui.components.MarkdownText
import com.jk.offermate.ui.components.PartialMarkdown
import com.jk.offermate.ui.components.TraceLabels
import com.jk.offermate.ui.components.traced
import com.jk.offermate.ui.components.StreamingMarkdown
import com.jk.offermate.ui.components.StreamingMarkdownTail
import com.jk.offermate.ui.components.rememberTypewriterText
import com.jk.offermate.ui.navigation.DockPillHeight
import com.jk.offermate.ui.theme.Indigo
import com.jk.offermate.ui.theme.IndigoContainer
import com.jk.offermate.ui.theme.OnIndigoContainer
import com.jk.offermate.ui.theme.OutlineSoft
import com.jk.offermate.ui.theme.TextPrimary
import com.jk.offermate.ui.theme.TextSecondary
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.launch

// 输入区/气泡的局部配色，贴近设计稿
private val InputBarBg = Color(0xFFF2F3F7)
private val UserBubble = Color(0xFFECECFE)

/** 流式贴底的节流间隔：约 12 次/秒，足够跟手又不会每 token 都触发滚动。 */
private const val STICK_THROTTLE_MS = 80L

/** 判定“仍在底部”的容差（px）：留一点余量，避免像素级抖动导致自动贴底反复开关。 */
private const val STICK_TOLERANCE_PX = 80

/** 消息之间的间距。补在每条消息的首行上，而不是用 spacedBy——同一条回答的块之间不该有它。 */
private val MESSAGE_SPACING = 18.dp

@OptIn(ExperimentalMaterial3Api::class, FlowPreview::class)
@Composable
fun FollowUpScreen(
    question: AnsweredQuestion?,
    conversations: List<ConversationEntity> = emptyList(),
    activeConversationId: String? = null,
    /** 已落库消息。**不含**正在流式生成的那条，后者走 [streamingText]。 */
    messages: List<ChatMessage>,
    sending: Boolean,
    /**
     * 是否正在**流式生成**（区别于 [sending]：更新答案等操作也会置 sending）。
     * 仅当为 true 时，流式气泡才走打字机逐字揭示；置 false 即定稿、一次性全量显示。
     */
    streaming: Boolean = false,
    /** 是否在列表末尾显示流式气泡。与 [messages] 原子更新，避免流式结束瞬间闪烁。 */
    showStreamingTail: Boolean = false,
    /**
     * 取当前的流式全文。**刻意收 lambda 而不是值**：这样它的 state 读取发生在真正显示文字的
     * 叶子 composable 里，而不是这个页面级 composable——否则每个 token 都要重组整页。
     */
    streamingText: () -> String? = { null },
    error: String?,
    notice: String?,
    onBack: () -> Unit,
    onUpdateAnswer: () -> Unit,
    /** 是否允许「用讨论更新答案」：同一段讨论只允许一次，需有新对话后才再次开放。 */
    canUpdateAnswer: Boolean = false,
    onNewSession: () -> Unit = {},
    onSwitchSession: (String) -> Unit = {},
    onConsumeError: () -> Unit,
    onConsumeNotice: () -> Unit,
    /** 若提供，则顶部栏显示菜单（抽屉）图标而非返回箭头（AI 对话 Tab 内嵌用）。 */
    onOpenDrawer: (() -> Unit)? = null,
    /** 顶部标题覆盖：非空时优先展示（AI 对话用首轮摘要标题）。 */
    titleOverride: String? = null,
    /** 内容底部留白：为悬浮 dock（输入胶囊 + Tab 胶囊）预留空间。 */
    contentBottomPadding: Dp = 0.dp,
    /** 从搜索结果进入时，要定位到的消息下标；非空则打开后滚动到该条而非底部。 */
    scrollToIndex: Int? = null,
    /** 定位完成回调（消费一次性目标）。 */
    onScrollConsumed: () -> Unit = {}
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    // 打字机的状态留在**页面作用域**：它不能待在 LazyColumn 的 item 里，item 会随滚动被回收、
    // 状态一丢就会从头重打一遍。但它的**输出是 State**，读取点下沉到流式尾行那个叶子，
    // 所以每个出字节拍只重组那一行，不再重组整页。
    val revealed = rememberTypewriterText(
        fullText = { streamingText().orEmpty() },
        isStreaming = streaming
    )

    // 流式文本的切块结果。两层 derivedStateOf 是关键：
    // - 整体（含正在生成的尾块）每个出字节拍都变，只被下面两个派生值消费；
    // - [settledBlocks] 掉最后一块后，内容在块与块之间是**不变的**，derivedStateOf 自带结果
    //   相等性检查，于是它只在「又写完一块」时才通知下游（约 1~2 次/秒），页面重组频率就降到这个量级。
    val streamBlocks = remember {
        derivedStateOf {
            traced(TraceLabels.BLOCKS) {
                StreamingMarkdown.blocks(PartialMarkdown.sanitize(revealed.value))
            }
        }
    }
    val settledBlocksState = remember { derivedStateOf { streamBlocks.value.dropLast(1) } }
    val tailBlockState = remember { derivedStateOf { streamBlocks.value.lastOrNull().orEmpty() } }

    // 已落库消息展开成行。**不含流式内容**，所以只在真正收到新消息时重建。
    val chatRows = remember(messages) { buildChatRows(messages) }
    val rows = chatRows.rows

    // 页面级读取，但因为上面的相等性检查，这里是低频的（只在「又写完一块」时变）。
    // 非流式时**不读**，这样页面就完全不订阅流式 state，空闲态一次多余重组都没有。
    val settledBlocks = if (showStreamingTail) settledBlocksState.value else emptyList()

    // 「正在思考」只在流式气泡还没出现时显示（如 updateAnswerFromDiscussion 那种不流式的等待）。
    // 流式开始后，等待态由流式气泡自己承担（首个 token 到达前它显示思考指示），
    // 这样就不必在页面级去读「流式文本是否还是空」，省掉一个高频读取点。
    val showThinking = sending && !showStreamingTail

    // 列表末尾附加项的数量：流式的已固化块 + 流式尾行 + 「正在思考」。
    val trailingItems = (if (showStreamingTail) settledBlocks.size + 1 else 0) + if (showThinking) 1 else 0
    val lastItemIndex = rows.size + trailingItems - 1

    // 这些都是普通参数、不是 snapshot state，长驻的 LaunchedEffect 直接捕获会读到启动那一刻的旧值。
    // 包一层 rememberUpdatedState 让下面的 snapshotFlow / derivedStateOf 能观察到更新。
    val currentScrollTo by rememberUpdatedState(scrollToIndex)
    val currentRows by rememberUpdatedState(chatRows)
    val currentLastItem by rememberUpdatedState(lastItemIndex)

    // 有搜索跳转目标时优先定位到命中消息；否则新消息到达时自动滚到底部。
    // scrollToIndex 是**消息下标**，必须换算成 item 下标。
    LaunchedEffect(scrollToIndex, messages.size) {
        if (scrollToIndex != null && rows.isNotEmpty()) {
            listState.scrollToItem(chatRows.rowOfMessage(scrollToIndex))
            onScrollConsumed()
        }
    }
    LaunchedEffect(messages.size) {
        if (scrollToIndex == null && rows.isNotEmpty()) {
            listState.animateScrollToItem(lastItemIndex.coerceAtLeast(0))
        }
    }

    // 用户主动上滑后暂停自动贴底，滑回接近底部再自动恢复——否则生成期间自动滚动会和手动滚动打架。
    // 这里用 totalItemsCount 而非消息数，展开后依然成立。
    val stickToBottom = remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull() ?: return@derivedStateOf true
            last.index >= info.totalItemsCount - 1 &&
                last.offset + last.size <= info.viewportEndOffset + STICK_TOLERANCE_PX
        }
    }

    // 流式生长时持续贴底。两点与最初实现不同：
    // 1. 节流：原来 LaunchedEffect(lastLen) 每个 token 都会取消重启一次；这里合并到 ~12 次/秒。
    // 2. 精确贴底：scrollToItem 只把 item **顶部**对齐视口顶部，气泡一长起来新生成的文字
    //    反而被顶到屏幕外。改用 scrollBy 直接推进偏移，等价于 Web 端直接写 scrollTop。
    LaunchedEffect(listState) {
        // 读的是打字机**已揭示**的长度，而不是已接收的长度——贴底要跟着看得见的文字走。
        // snapshotFlow 在协程里读 state，不会引起重组；derivedStateOf 有缓存，这里不额外分配。
        snapshotFlow { revealed.value.length }
            .sample(STICK_THROTTLE_MS)
            .collect {
                val target = currentLastItem
                if (currentScrollTo != null || target < 0 || !stickToBottom.value) return@collect
                val info = listState.layoutInfo
                val lastItem = info.visibleItemsInfo.lastOrNull { it.index == target }
                if (lastItem == null) {
                    listState.scrollToItem(target)
                } else {
                    val overflow = (lastItem.offset + lastItem.size - info.viewportEndOffset).toFloat()
                    if (overflow > 0f) listState.scrollBy(overflow)
                }
            }
    }

    // 是否显示“回到底部”悬浮按钮：最后一个 item 未完全可见时显示
    val showScrollDown by remember {
        derivedStateOf {
            val layout = listState.layoutInfo
            val lastVisible = layout.visibleItemsInfo.lastOrNull()?.index ?: 0
            currentRows.rows.isNotEmpty() && lastVisible < currentLastItem
        }
    }

    // “用本轮讨论更新答案”入口：仅绑定题目（追问）的会话，且自上次更新以来又有新对话时才显示，
    // 避免同一段讨论重复触发、空调 API。
    val showUpdateButton = canUpdateAnswer && question != null && messages.any { it.role == Role.ASSISTANT }
    // 更新答案悬浮条为消息列表额外预留的底部空间，避免最后一条消息被它盖住。
    val updateButtonReserve = if (showUpdateButton) 52.dp else 0.dp

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            // 键盘弹起时整体上移。不再用底部 padding 预留 dock 空间——改由列表 contentPadding 承担，
            // 让消息内容可以滚动穿过悬浮 dock 下方，而不是被截断在一个“容器”里。
            .imePadding()
    ) {
        ChatTopBar(
            title = titleOverride?.takeIf { it.isNotBlank() }
                ?: conversationTitle(conversations, activeConversationId, question),
            onBack = onBack,
            onNewSession = onNewSession,
            onOpenDrawer = onOpenDrawer
        )

        // 会话切换器：一道题可以有多轮独立讨论，横向切换 + 新开一轮
        if (conversations.size > 1) {
            SessionSwitcherRow(
                conversations = conversations,
                activeConversationId = activeConversationId,
                enabled = !sending,
                onSwitchSession = onSwitchSession,
                onNewSession = onNewSession
            )
        }

        // 会话消息
        Box(Modifier.weight(1f)) {
            if (messages.isEmpty()) {
                EmptyState(question)
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    // 底部留白：为悬浮 dock（+ 追问时的更新答案悬浮条）预留空间，
                    // 让内容可滚动穿过它们下方，而不是被截断在容器里。
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = 16.dp,
                        bottom = 16.dp + contentBottomPadding + updateButtonReserve
                    )
                    // 不能用 verticalArrangement = spacedBy(18.dp)：一条 AI 回答现在是多个 item，
                    // 那样会把消息间距塞进同一条回答的块之间。改为只给「消息首行」补上间距。
                ) {
                    itemsIndexed(
                        items = rows,
                        key = { _, row -> row.key },
                        contentType = { _, row -> row.contentType }
                    ) { index, row ->
                        val topPadding = if (row.isMessageStart && index > 0) MESSAGE_SPACING else 0.dp
                        Box(Modifier.fillMaxWidth().padding(top = topPadding)) {
                            ChatRowContent(row)
                        }
                    }

                    if (showStreamingTail) {
                        // 流式消息里**已经写完**的块：内容不再变化，和历史消息一样各自独立成 item，
                        // 既能命中 MarkdownStateCache，也只在进入视口时才组合测量。
                        itemsIndexed(
                            items = settledBlocks,
                            key = { index, _ -> "s$index" },
                            contentType = { _, _ -> "ai" }
                        ) { index, block ->
                            val topPadding = if (index == 0 && rows.isNotEmpty()) MESSAGE_SPACING else 0.dp
                            Box(Modifier.fillMaxWidth().padding(top = topPadding)) {
                                MarkdownText(markdown = block, modifier = Modifier.fillMaxWidth())
                            }
                        }
                        // 正在生成的那一块：整个流式期间只有**这一个 item** 在高频重组。
                        item(key = "streamingTail", contentType = "ai") {
                            val topPadding =
                                if (settledBlocks.isEmpty() && rows.isNotEmpty()) MESSAGE_SPACING else 0.dp
                            Box(Modifier.fillMaxWidth().padding(top = topPadding)) {
                                StreamingTailRow(tailBlockState)
                            }
                        }
                    }

                    // 还没进入流式的等待态（如「用讨论更新答案」）才显示思考指示。
                    if (showThinking) {
                        item(key = "thinking") {
                            Box(Modifier.fillMaxWidth().padding(top = MESSAGE_SPACING)) {
                                ThinkingIndicator()
                            }
                        }
                    }
                }
            }

            // 回到底部悬浮按钮（避让悬浮 dock 与更新答案悬浮条）
            if (showScrollDown) {
                Box(
                    Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 16.dp, bottom = 12.dp + contentBottomPadding + updateButtonReserve)
                ) {
                    ScrollToBottomButton {
                        scope.launch { listState.animateScrollToItem(lastItemIndex.coerceAtLeast(0)) }
                    }
                }
            }

            // 底部附加元素作为**悬浮 overlay** 叠在消息之上、避让 dock：
            // 消息可从其下方滚动穿过，不再形成“坐在容器上”的实心底栏。
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    // 与悬浮 dock（输入框）一致的 24dp 水平内边距，且左对齐——
                    // 让“更新答案”与输入框左边缘对齐。
                    .padding(horizontal = 24.dp)
                    .padding(bottom = contentBottomPadding),
                horizontalAlignment = Alignment.Start,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                error?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                    LaunchedEffect(it) {
                        kotlinx.coroutines.delay(4000)
                        onConsumeError()
                    }
                }
                notice?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    LaunchedEffect(it) {
                        kotlinx.coroutines.delay(3000)
                        onConsumeNotice()
                    }
                }
                if (showUpdateButton) {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        // 半透明浮层，与输入框一致，显得真正悬浮、不遮挡下方内容。
                        color = IndigoContainer.copy(alpha = 0.82f),
                        shadowElevation = 6.dp,
                        modifier = Modifier.clickable(enabled = !sending, onClick = onUpdateAnswer)
                    ) {
                        Text(
                            "用本轮讨论更新答案",
                            style = MaterialTheme.typography.labelLarge,
                            color = OnIndigoContainer,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }
                }
            }
        }

        // 底部附加元素（错误/通知/更新答案）已作为悬浮 overlay 叠在消息区之上（见上方 Box 内）。
        // 底部输入胶囊则上提到 app 级常驻 dock（见 BottomDock），此处不再渲染。
    }
}

/**
 * 单行紧凑输入胶囊：文本框 + 发送，整体高度与 Tab 胶囊一致。
 * 供 app 级常驻 dock（[com.jk.offermate.ui.navigation.BottomDock]）作为输入层复用。
 */
@Composable
internal fun CompactChatInput(
    input: String,
    onInputChange: (String) -> Unit,
    sending: Boolean,
    onSend: () -> Unit
) {
    val canSend = !sending && input.isNotBlank()
    Surface(
        shape = RoundedCornerShape(28.dp),
        // 与首页 Tab 胶囊一致的半透明浮层，让下方内容透出、显得真正悬浮而非“坐在容器上”。
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.82f),
        shadowElevation = 8.dp,
        modifier = Modifier.fillMaxWidth().height(DockPillHeight)
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(start = 18.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.weight(1f)) {
                if (input.isEmpty()) {
                    Text("发消息…", color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
                }
                androidx.compose.foundation.text.BasicTextField(
                    value = input,
                    onValueChange = onInputChange,
                    enabled = !sending,
                    singleLine = true,
                    textStyle = androidx.compose.material3.LocalTextStyle.current.copy(
                        color = TextPrimary,
                        fontSize = MaterialTheme.typography.bodyMedium.fontSize
                    ),
                    cursorBrush = androidx.compose.ui.graphics.SolidColor(Indigo),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Spacer(Modifier.width(8.dp))
            CircleActionButton(
                icon = Icons.AutoMirrored.Filled.Send,
                contentDescription = "发送",
                enabled = canSend,
                filled = true,
                onClick = { if (canSend) onSend() }
            )
        }
    }
}

/** 顶部栏：返回 + 居中标题（含“快速模式”副标题） + 新对话。 */
@Composable
private fun ChatTopBar(
    title: String,
    onBack: () -> Unit,
    onNewSession: () -> Unit,
    onOpenDrawer: (() -> Unit)? = null
) {
    Surface(color = MaterialTheme.colorScheme.background) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 4.dp, vertical = 6.dp)
        ) {
            if (onOpenDrawer != null) {
                IconButton(onClick = onOpenDrawer) {
                    Icon(
                        Icons.Filled.Menu,
                        contentDescription = "对话历史",
                        tint = TextPrimary
                    )
                }
            } else {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "返回",
                        tint = TextPrimary
                    )
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )
            }
            IconButton(onClick = onNewSession) {
                Icon(Icons.Filled.Add, contentDescription = "新对话", tint = TextPrimary)
            }
        }
    }
}

@Composable
private fun EmptyState(question: AnsweredQuestion?) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            question?.let {
                Text(
                    it.question,
                    style = MaterialTheme.typography.titleSmall,
                    color = TextPrimary,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(12.dp))
            }
            Text(
                text = if (question != null) {
                    "就这道题向 AI 追问，例如：\n“能换个更简单的角度解释吗？”\n“结合我的项目怎么答？”"
                } else {
                    "问我任何面试相关的问题，例如：\n“介绍一下 JVM 内存模型”\n“如何准备系统设计面试？”"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun ThinkingIndicator() {
    Row(
        Modifier.fillMaxWidth().padding(top = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CircularProgressIndicator(
            Modifier.size(16.dp),
            strokeWidth = 2.dp,
            color = Indigo
        )
        Spacer(Modifier.width(8.dp))
        Text("AI 正在思考…", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
    }
}

@Composable
private fun ScrollToBottomButton(onClick: () -> Unit) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, OutlineSoft),
        shadowElevation = 3.dp,
        modifier = Modifier.size(40.dp).clickable(onClick = onClick)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                Icons.Filled.KeyboardArrowDown,
                contentDescription = "回到底部",
                tint = TextSecondary
            )
        }
    }
}

@Composable
private fun SessionSwitcherRow(
    conversations: List<ConversationEntity>,
    activeConversationId: String?,
    enabled: Boolean,
    onSwitchSession: (String) -> Unit,
    onNewSession: () -> Unit
) {
    LazyRow(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        itemsIndexed(conversations) { index, conversation ->
            val selected = conversation.id == activeConversationId
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = if (selected) IndigoContainer else MaterialTheme.colorScheme.surface,
                border = if (selected) null else BorderStroke(1.dp, OutlineSoft),
                modifier = Modifier.clickable(enabled = enabled) { onSwitchSession(conversation.id) }
            ) {
                Text(
                    text = "第 ${index + 1} 轮",
                    style = MaterialTheme.typography.labelLarge,
                    color = if (selected) OnIndigoContainer else TextSecondary,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                )
            }
        }
        item {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, OutlineSoft),
                modifier = Modifier.clickable(enabled = enabled, onClick = onNewSession)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Icon(Icons.Filled.Add, contentDescription = "新开一轮", modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("新开一轮", style = MaterialTheme.typography.labelLarge, color = TextSecondary)
                }
            }
        }
    }
}

/** 渲染展开后的一行。 */
@Composable
private fun ChatRowContent(row: ChatRow) {
    when (row) {
        is ChatRow.User -> UserBubbleRow(row.content)
        // 已写完的块内容不再变化：同步解析（单块很小，且多数已由 ChatViewModel 预热）并入缓存，
        // 之后每次进入视口都是缓存命中。
        is ChatRow.AiBlock -> MarkdownText(
            markdown = row.text,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/**
 * 流式生成中的那一块。
 *
 * **整个流式期间只有这个 composable 在高频重组**——这是把重组范围压到叶子的落点：
 * [tailBlock] 的 `.value` 读取发生在这里，所以打字机每次出字只让这一行失效，
 * 页面、列表、以及前面所有已固化的块都不受影响。
 *
 * 首个 token 到达前尾块是空的，此时显示思考指示，避免气泡先闪一个空白行。
 */
@Composable
private fun StreamingTailRow(tailBlock: State<String>) {
    val text = tailBlock.value
    if (text.isBlank()) {
        ThinkingIndicator()
        return
    }
    // 渲染策略（轻量单节点 + 尾部渐显，或退回完整渲染器）都在 StreamingMarkdownTail 里。
    StreamingMarkdownTail(text = text, modifier = Modifier.fillMaxWidth())
}

/** 用户消息：右对齐气泡。 */
@Composable
private fun UserBubbleRow(content: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Surface(
            shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = 18.dp, bottomEnd = 6.dp),
            color = UserBubble,
            modifier = Modifier.widthIn(max = 300.dp)
        ) {
            Text(
                content,
                color = OnIndigoContainer,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
            )
        }
    }
}

/** 底部输入区：圆角输入框 + 功能胶囊（深度思考/智能搜索） + 新开/发送。 */
@Composable
private fun ChatInputBar(
    input: String,
    onInputChange: (String) -> Unit,
    sending: Boolean,
    deepThink: Boolean,
    webSearch: Boolean,
    onToggleDeepThink: () -> Unit,
    onToggleWebSearch: () -> Unit,
    onAdd: () -> Unit,
    onSend: () -> Unit
) {
    val canSend = !sending && input.isNotBlank()
    Surface(
        color = MaterialTheme.colorScheme.background,
        shadowElevation = 8.dp
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            // 输入框
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = InputBarBg,
                modifier = Modifier.fillMaxWidth()
            ) {
                TextField(
                    value = input,
                    onValueChange = onInputChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp),
                    placeholder = {
                        Text("发消息或按住说话", color = TextSecondary)
                    },
                    enabled = !sending,
                    maxLines = 5,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        disabledContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        disabledIndicatorColor = Color.Transparent
                    )
                )
            }

            Spacer(Modifier.height(10.dp))

            // 功能胶囊 + 操作按钮
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FeatureChip(
                    label = "深度思考",
                    selected = deepThink,
                    onClick = onToggleDeepThink
                )
                Spacer(Modifier.width(8.dp))
                FeatureChip(
                    label = "智能搜索",
                    selected = webSearch,
                    leadingIcon = Icons.Filled.Search,
                    onClick = onToggleWebSearch
                )

                Spacer(Modifier.weight(1f))

                CircleActionButton(
                    icon = Icons.Filled.Add,
                    contentDescription = "新开一轮",
                    onClick = onAdd
                )
                Spacer(Modifier.width(10.dp))
                CircleActionButton(
                    icon = Icons.AutoMirrored.Filled.Send,
                    contentDescription = "发送",
                    enabled = canSend,
                    filled = true,
                    onClick = { if (canSend) onSend() }
                )
            }
        }
    }
}

@Composable
private fun FeatureChip(
    label: String,
    selected: Boolean,
    leadingIcon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    onClick: () -> Unit
) {
    val bg = if (selected) IndigoContainer else MaterialTheme.colorScheme.surface
    val fg = if (selected) OnIndigoContainer else TextSecondary
    val border = if (selected) null else BorderStroke(1.dp, OutlineSoft)
    Surface(
        shape = CircleShape,
        color = bg,
        border = border,
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
        ) {
            leadingIcon?.let {
                Icon(it, contentDescription = null, tint = fg, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(4.dp))
            }
            Text(label, style = MaterialTheme.typography.labelLarge, color = fg)
        }
    }
}

@Composable
private fun CircleActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    enabled: Boolean = true,
    filled: Boolean = false,
    onClick: () -> Unit
) {
    val background = when {
        filled && enabled -> Indigo
        filled -> Indigo.copy(alpha = 0.35f)
        else -> MaterialTheme.colorScheme.surface
    }
    val tint = if (filled) Color.White else TextPrimary
    Surface(
        shape = CircleShape,
        color = background,
        border = if (filled) null else BorderStroke(1.dp, OutlineSoft),
        modifier = Modifier
            .size(40.dp)
            .clickable(enabled = enabled, onClick = onClick)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = contentDescription, tint = tint, modifier = Modifier.size(20.dp))
        }
    }
}

/** 用 Canvas 画一个小闪电，代表“快速模式”（core 图标集无闪电图标）。 */
@Composable
private fun BoltIcon(tint: Color, modifier: Modifier = Modifier) {
    androidx.compose.foundation.Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val path = Path().apply {
            moveTo(w * 0.55f, 0f)
            lineTo(w * 0.15f, h * 0.55f)
            lineTo(w * 0.45f, h * 0.55f)
            lineTo(w * 0.42f, h)
            lineTo(w * 0.85f, h * 0.40f)
            lineTo(w * 0.52f, h * 0.40f)
            close()
        }
        drawPath(path, color = tint)
    }
}

/** 会话标题：优先取当前会话标题，否则用题目文本，兜底“追问”。 */
private fun conversationTitle(
    conversations: List<ConversationEntity>,
    activeConversationId: String?,
    question: AnsweredQuestion?
): String {
    val active = conversations.firstOrNull { it.id == activeConversationId }
    return active?.title?.takeIf { it.isNotBlank() }
        ?: question?.question?.takeIf { it.isNotBlank() }
        ?: "新对话"
}
