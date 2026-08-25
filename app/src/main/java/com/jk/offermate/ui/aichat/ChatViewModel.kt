package com.jk.offermate.ui.aichat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.jk.offermate.agent.pipeline.AnsweredQuestion
import com.jk.offermate.agent.ChatMessage
import com.jk.offermate.agent.Role
import com.jk.offermate.agent.chat.FollowUpService
import com.jk.offermate.agent.chat.QuestionContext
import com.jk.offermate.data.repository.ConversationRepository
import com.jk.offermate.data.repository.QuestionRepository
import com.jk.offermate.ui.components.MarkdownStateCache
import com.jk.offermate.ui.components.StreamingMarkdown
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 以「会话」为中心的对话 VM：会话是可选的——
 * [initialConversationId] 为空即一段**全新空白对话**，直到用户发第一条消息才懒创建会话记录。
 * [questionId] 为空即自由对话；非空则绑定该题（附加上下文，且可「用讨论更新答案」）。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModel(
    initialConversationId: String?,
    private val questionId: String?,
    private val questionRepository: QuestionRepository,
    private val conversationRepository: ConversationRepository,
    private val followUpService: FollowUpService
) : ViewModel() {

    /** 当前会话 id；null 表示尚未创建（空白对话）。 */
    private val conversationId = MutableStateFlow(initialConversationId)

    /** 绑定的题目（自由对话为 null）。 */
    val question: StateFlow<AnsweredQuestion?> =
        (if (questionId == null) flowOf(null) else questionRepository.observeById(questionId))
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val persistedMessages: StateFlow<List<ChatMessage>> =
        conversationId
            .flatMapLatest { id ->
                if (id == null) flowOf(emptyList()) else conversationRepository.observeMessages(id)
            }
            // 在消息交给 UI **之前**，先在后台把 AI 回复的 Markdown 解析好写入 MarkdownStateCache。
            // 这样 MarkdownText 首次组合即命中缓存，跳过 State.Loading（空白）那一帧，
            // 不会出现“进入会话页先看到用户消息、AI 回答慢一拍才出现”。
            .map { list -> list.also { prewarmMarkdown(it) } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 正在流式生成中的 AI 文本（null 表示当前无流式）。 */
    private val _streaming = MutableStateFlow<String?>(null)

    /**
     * 是否正处于**流式生成**中。UI 用它判断最后一条气泡要不要走打字机。
     *
     * 不能用 [sending] 代替：`updateAnswerFromDiscussion()` 也会把 sending 置真，
     * 那时最后一条是已落库的历史消息，若被当成流式就会整条重新“打”一遍。
     */
    private val _streamingActive = MutableStateFlow(false)
    val streamingActive: StateFlow<Boolean> = _streamingActive.asStateFlow()

    /**
     * 供 UI 渲染的消息：在已落库消息之上，追加“正在流式生成”的临时 AI 气泡。
     * 若最新一条已落库消息内容与流式文本相同（生成完成、已入库），则去重、不重复展示。
     */
    val messages: StateFlow<List<ChatMessage>> =
        combine(persistedMessages, _streaming) { db, streaming ->
            when {
                streaming == null -> db
                db.lastOrNull()?.let { it.role == Role.ASSISTANT && it.content == streaming } == true -> db
                else -> db + ChatMessage(role = Role.ASSISTANT, content = streaming)
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 当前会话标题：取自会话记录（首轮对话后由摘要生成）；无标题/无会话时为 null。 */
    val title: StateFlow<String?> =
        conversationId
            .flatMapLatest { id ->
                if (id == null) flowOf(null) else conversationRepository.observeConversation(id)
            }
            .map { it?.title?.takeIf { t -> t.isNotBlank() } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _sending = MutableStateFlow(false)
    val sending: StateFlow<Boolean> = _sending.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _notice = MutableStateFlow<String?>(null)
    val notice: StateFlow<String?> = _notice.asStateFlow()

    /** 该会话「上次更新答案时的消息条数」（持久化，退出重开仍有效）；从未更新过为 -1。 */
    private val lastUpdatedMsgCount: StateFlow<Int> =
        conversationId
            .flatMapLatest { id ->
                if (id == null) flowOf(-1) else conversationRepository.observeAnswerUpdatedCount(id)
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), -1)

    /**
     * 是否可以「用讨论更新答案」：绑定题目、已有 AI 回复，且**自上次更新以来又有新的对话**。
     * 这样避免在没有新内容时重复触发，空调 API 造成浪费。标记已持久化，退出 app 重开仍生效。
     */
    val canUpdateAnswer: StateFlow<Boolean> =
        combine(persistedMessages, lastUpdatedMsgCount) { msgs, marker ->
            questionId != null &&
                msgs.any { it.role == Role.ASSISTANT } &&
                msgs.size > marker
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun send(text: String) {
        val content = text.trim()
        if (content.isEmpty() || _sending.value) return
        viewModelScope.launch {
            _error.value = null
            _sending.value = true
            try {
                val convId = ensureConversation()
                // 记录是否为首轮：追加用户消息前历史为空即首轮，用于生成一次性标题。
                val isFirstRound = conversationRepository.history(convId).isEmpty()
                conversationRepository.append(convId, Role.USER, content)
                // 开始流式：置空占位，随 token 到达增量拼接。
                _streaming.value = ""
                _streamingActive.value = true
                val ctx = currentContext()
                val history = conversationRepository.history(convId)
                val reply = streamBatched { onDelta ->
                    followUpService.replyStreaming(
                        context = ctx,
                        history = history,
                        onDelta = onDelta
                    )
                }
                // 定稿为完整回复（与入库内容一致，供 messages 去重）。
                _streaming.value = reply
                // 顺序要紧：先把定稿内容按块解析好入缓存，**再**关闭流式态。关闭流式态时
                // UI 会从「打字机 + 异步尾巴」切到「按块同步渲染」，缓存已就绪才不会掉帧。
                runCatching { warmBlocks(reply) }
                _streamingActive.value = false
                conversationRepository.append(convId, Role.ASSISTANT, reply)
                runCatching { maybeGenerateTitle(convId, isFirstRound, content, reply) }
            } catch (e: Exception) {
                _streaming.value = null
                _error.value = e.message ?: "回复失败，请稍后重试"
            } finally {
                _streamingActive.value = false
                _sending.value = false
            }
        }
    }

    /** 综合讨论重写该题答案（仅绑定题目、且已有会话时可用）。 */
    fun updateAnswerFromDiscussion() {
        if (_sending.value) return
        val qId = questionId ?: return
        val convId = conversationId.value
        if (convId == null) {
            _error.value = "先聊几轮再更新答案吧"
            return
        }
        // 二次校验：同一段讨论没有新对话时不重复更新，避免空调 API。
        if (!canUpdateAnswer.value) {
            _notice.value = "继续讨论后再更新答案吧"
            return
        }
        viewModelScope.launch {
            _error.value = null
            _sending.value = true
            try {
                val ctx = currentContext()
                if (ctx == null) {
                    _error.value = "题目尚未加载，请稍候"
                    return@launch
                }
                val history = conversationRepository.history(convId)
                if (history.isEmpty()) {
                    _error.value = "先聊几轮再更新答案吧"
                    return@launch
                }
                val revised = followUpService.reviseAnswer(
                    context = ctx,
                    history = history
                )
                if (revised.isNotBlank()) {
                    questionRepository.updateAnswer(qId, revised)
                    // 持久化记录本次更新时的消息条数：需再有新对话（条数增长）才允许下次更新。
                    conversationRepository.markAnswerUpdated(convId, history.size)
                    _notice.value = "答案已根据讨论更新"
                }
            } catch (e: Exception) {
                _error.value = e.message ?: "更新答案失败，请稍后重试"
            } finally {
                _sending.value = false
            }
        }
    }

    /**
     * 运行一次流式请求，把 IO 线程到达的增量与 UI 状态发布**解耦**后写入 [_streaming]。
     *
     * 替代原来的 `_streaming.value = (_streaming.value ?: "") + delta`，解决两个问题：
     * 1. 那种写法每个 token 都全量复制一次字符串，整段回复累计 O(n²) 次字符拷贝 + 同等规模的垃圾对象。
     *    这里用 [StringBuilder] 追加，摊还 O(1)。
     * 2. 每个 token 发布一次状态。这里每次唤醒会把通道里**已经到达的增量一次性排干**再发布，
     *    provider 突发推送时自然合并成一次 UI 更新。
     *
     * [builder] 只被 pump 协程访问（增量经 Channel 从 IO 线程转交），因此无需加锁。
     *
     * @param run 实际发起流式请求，参数为要传给底层的 onDelta 回调
     */
    private suspend fun streamBatched(run: suspend (onDelta: (String) -> Unit) -> String): String =
        coroutineScope {
            val deltas = Channel<String>(Channel.UNLIMITED)
            val builder = StringBuilder()
            val pump = launch {
                while (true) {
                    // 先挂起等第一个增量；拿到后把通道里剩下的全部排干，合并为一次发布。
                    val first = deltas.receiveCatching().getOrNull() ?: break
                    builder.append(first)
                    while (true) builder.append(deltas.tryReceive().getOrNull() ?: break)
                    _streaming.value = builder.toString()
                }
            }
            try {
                run { delta -> deltas.trySend(delta) }
            } finally {
                deltas.close()
                pump.join()
            }
        }

    fun consumeError() { _error.value = null }
    fun consumeNotice() { _notice.value = null }

    /**
     * 在消息切到 UI 前，后台预解析该会话所有 AI 回复的 Markdown 并写入 [MarkdownStateCache]，
     * 让 [com.jk.offermate.ui.components.MarkdownText] 首次组合即命中缓存，跳过空白帧。
     *
     * 只预热正文非空的 AI 消息；用户消息是纯文本，不走 Markdown 渲染。
     */
    private suspend fun prewarmMarkdown(messages: List<ChatMessage>) {
        messages.asSequence()
            .filter { it.role == Role.ASSISTANT && it.content.isNotBlank() }
            .forEach { warmBlocks(it.content) }
    }

    /**
     * 按**块**预热，而不是整条消息。
     *
     * UI 侧 AI 消息已按 Markdown 块展开成多个 LazyColumn item（见 ChatRows），
     * 渲染时的缓存 key 是单个块的文本，所以预热也必须按块来，否则一条都命中不上。
     */
    private suspend fun warmBlocks(content: String) {
        StreamingMarkdown.blocksMemo(content).forEach { block ->
            if (block.isNotBlank()) MarkdownStateCache.warm(block)
        }
    }

    /**
     * 首轮对话结束后，为**无标题**的会话生成一个摘要标题（仅一次，后续不再更新）。
     * 绑定题目的会话已用题目作标题，不受影响。
     */
    private suspend fun maybeGenerateTitle(
        convId: String,
        isFirstRound: Boolean,
        userText: String,
        reply: String
    ) {
        if (!isFirstRound) return
        val current = conversationRepository.observeConversation(convId).first()
        if (current != null && current.title.isNotBlank()) return
        val summary = runCatching { followUpService.summarizeTitle(userText, reply) }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }
        val title = summary ?: userText.trim().take(15)
        if (title.isNotBlank()) conversationRepository.updateTitle(convId, title)
    }

    /** 首次发送时懒创建会话：绑定题目走 getOrCreate，否则新建自由会话。 */
    private suspend fun ensureConversation(): String {
        conversationId.value?.let { return it }
        val id = if (questionId != null) {
            val q = question.value
            conversationRepository.getOrCreateForQuestion(questionId, q?.question.orEmpty())
        } else {
            conversationRepository.createNewChat("")
        }
        conversationId.value = id
        return id
    }

    private fun currentContext(): QuestionContext? {
        val q = question.value ?: return null
        return QuestionContext(question = q.question, currentAnswer = q.answer, tags = q.tags)
    }

    companion object {
        fun provideFactory(
            initialConversationId: String?,
            questionId: String?,
            questionRepository: QuestionRepository,
            conversationRepository: ConversationRepository,
            followUpService: FollowUpService
        ) = viewModelFactory {
            initializer {
                ChatViewModel(
                    initialConversationId,
                    questionId,
                    questionRepository,
                    conversationRepository,
                    followUpService
                )
            }
        }
    }
}
