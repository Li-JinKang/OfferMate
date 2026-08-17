package com.jk.offermate.ui.followup

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jk.offermate.agent.AnsweredQuestion
import com.jk.offermate.agent.ChatMessage
import com.jk.offermate.agent.Role
import com.jk.offermate.di.AppContainer
import com.jk.offermate.ui.components.MarkdownText
import com.jk.offermate.ui.theme.CardSurface
import com.jk.offermate.ui.theme.IndigoContainer
import com.jk.offermate.ui.theme.OnIndigoContainer
import com.jk.offermate.ui.theme.TextSecondary

@Composable
fun FollowUpRoute(container: AppContainer, questionId: String, onBack: () -> Unit) {
    val viewModel: FollowUpViewModel = viewModel(
        factory = FollowUpViewModel.provideFactory(
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
        messages = messages,
        sending = sending,
        error = error,
        notice = notice,
        onBack = onBack,
        onSend = viewModel::send,
        onUpdateAnswer = viewModel::updateAnswerFromDiscussion,
        onConsumeError = viewModel::consumeError,
        onConsumeNotice = viewModel::consumeNotice
    )
}

@Composable
fun FollowUpScreen(
    question: AnsweredQuestion?,
    messages: List<ChatMessage>,
    sending: Boolean,
    error: String?,
    notice: String?,
    onBack: () -> Unit,
    onSend: (String) -> Unit,
    onUpdateAnswer: () -> Unit,
    onConsumeError: () -> Unit,
    onConsumeNotice: () -> Unit
) {
    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .imePadding()
    ) {
        // 顶部：返回 + 题目
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)
        ) {
            TextButton(onClick = onBack) { Text("‹ 返回") }
            Text(
                text = "追问",
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        question?.let { q ->
            Surface(color = IndigoContainer) {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    Text(
                        q.question,
                        style = MaterialTheme.typography.titleSmall,
                        color = OnIndigoContainer
                    )
                    if (q.tags.isNotEmpty()) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            q.tags.joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                            color = OnIndigoContainer
                        )
                    }
                }
            }
        }

        // 会话消息
        Box(Modifier.weight(1f)) {
            if (messages.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "就这道题向 AI 追问，例如：\n“能换个更简单的角度解释吗？”\n“结合我的项目怎么答？”",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(messages.size) { index ->
                        MessageBubble(messages[index])
                    }
                    if (sending) {
                        item {
                            Row(
                                Modifier.fillMaxWidth().padding(top = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(Modifier.height(18.dp).widthIn(min = 18.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.widthIn(min = 8.dp))
                                Text("AI 正在思考…", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                            }
                        }
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
            OutlinedButton(
                onClick = onUpdateAnswer,
                enabled = !sending,
                modifier = Modifier.padding(horizontal = 16.dp)
            ) {
                Text("用本轮讨论更新答案")
            }
        }

        // 输入栏
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("输入你的追问…") },
                enabled = !sending,
                maxLines = 4
            )
            IconButton(
                onClick = {
                    onSend(input)
                    input = ""
                },
                enabled = !sending && input.isNotBlank()
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "发送")
            }
        }
    }
}

@Composable
private fun MessageBubble(message: ChatMessage) {
    val isUser = message.role == Role.USER
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (isUser) IndigoContainer else CardSurface
            ),
            modifier = Modifier.widthIn(max = 300.dp)
        ) {
            Box(Modifier.padding(12.dp)) {
                if (isUser) {
                    Text(message.content, color = OnIndigoContainer, style = MaterialTheme.typography.bodyMedium)
                } else {
                    MarkdownText(message.content)
                }
            }
        }
    }
}
