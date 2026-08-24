package com.jk.offermate.ui.followup

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import com.jk.offermate.ui.navigation.DockPillHeight
import com.jk.offermate.ui.theme.Indigo
import com.jk.offermate.ui.theme.IndigoContainer
import com.jk.offermate.ui.theme.OnIndigoContainer
import com.jk.offermate.ui.theme.OutlineSoft
import com.jk.offermate.ui.theme.TextPrimary
import com.jk.offermate.ui.theme.TextSecondary
import kotlinx.coroutines.launch

// 输入区/气泡的局部配色，贴近设计稿
private val InputBarBg = Color(0xFFF2F3F7)
private val UserBubble = Color(0xFFECECFE)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FollowUpScreen(
    question: AnsweredQuestion?,
    conversations: List<ConversationEntity> = emptyList(),
    activeConversationId: String? = null,
    messages: List<ChatMessage>,
    sending: Boolean,
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

    // 有搜索跳转目标时优先定位到命中消息；否则新消息到达时自动滚到底部。
    LaunchedEffect(scrollToIndex, messages.size) {
        if (scrollToIndex != null && messages.isNotEmpty()) {
            listState.scrollToItem(scrollToIndex.coerceIn(0, messages.lastIndex))
            onScrollConsumed()
        }
    }
    LaunchedEffect(messages.size) {
        if (scrollToIndex == null && messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }
    // 流式生长：最后一条 AI 气泡文本变长时持续贴底（消息条数不变，需单独追踪内容长度）。
    val lastLen = messages.lastOrNull()?.content?.length ?: 0
    LaunchedEffect(lastLen) {
        if (scrollToIndex == null && messages.isNotEmpty()) {
            listState.scrollToItem(messages.lastIndex)
        }
    }

    // 是否显示“回到底部”悬浮按钮：最后一条消息未完全可见时显示
    val showScrollDown by remember {
        derivedStateOf {
            val layout = listState.layoutInfo
            val lastVisible = layout.visibleItemsInfo.lastOrNull()?.index ?: 0
            messages.isNotEmpty() && lastVisible < messages.lastIndex
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
                    ),
                    verticalArrangement = Arrangement.spacedBy(18.dp)
                ) {
                    itemsIndexed(messages) { _, message ->
                        MessageBubble(message)
                    }
                    // 仅在“尚无 AI 气泡”时显示思考指示：流式首个 token 到达后，
                    // 最后一条已是正在生长的 AI 气泡，无需再显示“正在思考”。
                    if (sending && messages.lastOrNull()?.role != Role.ASSISTANT) {
                        item { ThinkingIndicator() }
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
                        scope.launch { listState.animateScrollToItem(messages.lastIndex) }
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

@Composable
private fun MessageBubble(message: ChatMessage) {
    if (message.role == Role.USER) {
        // 用户消息：右对齐气泡
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Surface(
                shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = 18.dp, bottomEnd = 6.dp),
                color = UserBubble,
                modifier = Modifier.widthIn(max = 300.dp)
            ) {
                Text(
                    message.content,
                    color = OnIndigoContainer,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                )
            }
        }
    } else {
        // AI 回答：铺满整屏、无气泡，Markdown 直接渲染（标题/列表/代码块/表格）
        MarkdownText(message.content, modifier = Modifier.fillMaxWidth())
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
