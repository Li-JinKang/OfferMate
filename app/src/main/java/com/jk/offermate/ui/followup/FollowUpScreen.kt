package com.jk.offermate.ui.followup

import androidx.compose.animation.core.animateDpAsState
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
import com.jk.offermate.agent.AnsweredQuestion
import com.jk.offermate.agent.ChatMessage
import com.jk.offermate.agent.Role
import com.jk.offermate.data.local.entity.ConversationEntity
import com.jk.offermate.ui.components.MarkdownText
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
    onSend: (String) -> Unit,
    onUpdateAnswer: () -> Unit,
    onNewSession: () -> Unit = {},
    onSwitchSession: (String) -> Unit = {},
    onConsumeError: () -> Unit,
    onConsumeNotice: () -> Unit,
    /** 若提供，则顶部栏显示菜单（抽屉）图标而非返回箭头（AI 对话 Tab 内嵌用）。 */
    onOpenDrawer: (() -> Unit)? = null,
    /** 若提供，则底部输入条与该 Tab 栏堆叠为可切换的悬浮 dock（AI 对话 Tab 内嵌用）。 */
    bottomTabs: (@Composable () -> Unit)? = null
) {
    var input by remember { mutableStateOf("") }
    var deepThink by remember { mutableStateOf(false) }
    var webSearch by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }

    // 是否显示“回到底部”悬浮按钮：最后一条消息未完全可见时显示
    val showScrollDown by remember {
        derivedStateOf {
            val layout = listState.layoutInfo
            val lastVisible = layout.visibleItemsInfo.lastOrNull()?.index ?: 0
            messages.isNotEmpty() && lastVisible < messages.lastIndex
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .imePadding()
    ) {
        ChatTopBar(
            title = conversationTitle(conversations, activeConversationId, question),
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
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp)
                ) {
                    itemsIndexed(messages) { _, message ->
                        MessageBubble(message)
                    }
                    if (sending) {
                        item { ThinkingIndicator() }
                    }
                }
            }

            // 回到底部悬浮按钮
            if (showScrollDown) {
                Box(
                    Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 16.dp, bottom = 12.dp)
                ) {
                    ScrollToBottomButton {
                        scope.launch { listState.animateScrollToItem(messages.lastIndex) }
                    }
                }
            }
        }

        error?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 16.dp)
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
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            LaunchedEffect(it) {
                kotlinx.coroutines.delay(3000)
                onConsumeNotice()
            }
        }

        // 更新答案入口
        if (messages.any { it.role == Role.ASSISTANT }) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = IndigoContainer,
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .clickable(enabled = !sending, onClick = onUpdateAnswer)
            ) {
                Text(
                    "用本轮讨论更新答案",
                    style = MaterialTheme.typography.labelLarge,
                    color = OnIndigoContainer,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
            Spacer(Modifier.height(4.dp))
        }

        if (bottomTabs != null) {
            // AI 对话 Tab：输入条与 Tab 栏堆叠为可切换 dock
            ChatBottomDock(
                input = input,
                onInputChange = { input = it },
                sending = sending,
                onSend = {
                    onSend(input)
                    input = ""
                },
                tabs = bottomTabs
            )
        } else {
            ChatInputBar(
                input = input,
                onInputChange = { input = it },
                sending = sending,
                deepThink = deepThink,
                webSearch = webSearch,
                onToggleDeepThink = { deepThink = !deepThink },
                onToggleWebSearch = { webSearch = !webSearch },
                onAdd = onNewSession,
                onSend = {
                    onSend(input)
                    input = ""
                }
            )
        }
    }
}

/** dock 中两个胶囊的统一高度与探头露出高度。 */
private val DockPillHeight = 60.dp
private val DockPeek = 18.dp

/**
 * 底部堆叠 dock：输入胶囊与 Tab 胶囊等高，前者在上、后者在下露出一条边（[DockPeek]）。
 * 点击露出的探头，两者带滑动动画互换前后位置。前面的完全可交互，后面的整块只作为“切换”热区。
 */
@Composable
private fun ChatBottomDock(
    input: String,
    onInputChange: (String) -> Unit,
    sending: Boolean,
    onSend: () -> Unit,
    tabs: @Composable () -> Unit
) {
    var inputInFront by rememberSaveable { mutableStateOf(true) }
    val inputOffset by animateDpAsState(if (inputInFront) 0.dp else DockPeek, label = "inputOffset")
    val tabsOffset by animateDpAsState(if (inputInFront) DockPeek else 0.dp, label = "tabsOffset")

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp)
            .padding(top = 4.dp, bottom = 10.dp)
            .height(DockPillHeight + DockPeek)
    ) {
        val inputLayer: @Composable () -> Unit = {
            DockLayer(offsetY = inputOffset, inFront = inputInFront, onTapPeek = { inputInFront = true }) {
                CompactChatInput(input = input, onInputChange = onInputChange, sending = sending, onSend = onSend)
            }
        }
        val tabsLayer: @Composable () -> Unit = {
            DockLayer(offsetY = tabsOffset, inFront = !inputInFront, onTapPeek = { inputInFront = false }) {
                tabs()
            }
        }
        // 后画的在上层：把当前在前的那个后画
        if (inputInFront) {
            tabsLayer(); inputLayer()
        } else {
            inputLayer(); tabsLayer()
        }
    }
}

@Composable
private fun BoxScope.DockLayer(
    offsetY: Dp,
    inFront: Boolean,
    onTapPeek: () -> Unit,
    content: @Composable () -> Unit
) {
    Box(
        modifier = Modifier
            .align(Alignment.TopCenter)
            .fillMaxWidth()
            .offset(y = offsetY)
            .height(DockPillHeight)
    ) {
        content()
        if (!inFront) {
            // 在后：整块作为切换热区（实际只有露出的探头可点），并拦截内部交互
            Box(
                Modifier
                    .matchParentSize()
                    .clip(RoundedCornerShape(28.dp))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onTapPeek
                    )
            )
        }
    }
}

/** 单行紧凑输入胶囊：文本框 + 发送，整体高度与 Tab 胶囊一致。 */
@Composable
private fun CompactChatInput(
    input: String,
    onInputChange: (String) -> Unit,
    sending: Boolean,
    onSend: () -> Unit
) {
    val canSend = !sending && input.isNotBlank()
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surface,
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

/** 顶部栏：返回 + 居中标题（含“快速模式”副标题） + 新开一轮。 */
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
                Spacer(Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    BoltIcon(tint = Indigo, modifier = Modifier.size(12.dp))
                    Spacer(Modifier.width(3.dp))
                    Text(
                        "快速模式",
                        style = MaterialTheme.typography.bodySmall,
                        color = Indigo
                    )
                }
            }
            IconButton(onClick = onNewSession) {
                Icon(Icons.Filled.Add, contentDescription = "新开一轮", tint = TextPrimary)
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
                "就这道题向 AI 追问，例如：\n“能换个更简单的角度解释吗？”\n“结合我的项目怎么答？”",
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
        ?: "追问"
}
