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
import com.jk.offermate.ui.components.InlineMarkdown
import com.jk.offermate.ui.components.MarkdownStateCache
import com.jk.offermate.ui.components.StreamingMarkdown
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 会话页一次渲染所需的列表内容。
 *
 * @property messages 已落库消息，**不含**正在流式生成的那条（它走 [ChatViewModel.streamingText]）
 * @property showStreamingTail 是否要在列表末尾显示流式气泡
 */
data class ChatContent(
    val messages: List<ChatMessage> = emptyList(),
    val showStreamingTail: Boolean = false
)

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
                // 换会话就重置预热进度，否则两个等长会话之间会误判「已经热过了」。
                prewarmedCount = 0
                if (id == null) flowOf(emptyList()) else conversationRepository.observeMessages(id)
            }
            // 在消息交给 UI **之前**，先在后台把 AI 回复的 Markdown 解析好写入 MarkdownStateCache。
            // 这样 MarkdownText 首次组合即命中缓存，跳过 State.Loading（空白）那一帧，
            // 不会出现“进入会话页先看到用户消息、AI 回答慢一拍才出现”。
            .map { list -> list.also { prewarmMarkdown(it) } }
            // 预热里的 blocksMemo / peek 循环是纯 CPU 活，不能放在 Main 上跑：
            // stateIn(viewModelScope) 会让上游默认落到 Main.immediate，长会话下这一圈
            // 「消息数 × 块数」次哈希查找就是一次主线程尖峰。
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 正在流式生成中的 AI 文本（null 表示当前无流式）。 */
    private val _streaming = MutableStateFlow<String?>(null)

    /**
     * 流式文本的**独立通道**，只给真正显示文字的那个叶子 composable 读。
     *
     * 为什么不再塞进 [content] 的消息列表里：那样每个 SSE chunk（provider 侧 20~50 次/秒）
     * 都会重建整个消息列表、触发页面级重组（重建上百个行对象 + 重跑 LazyColumn 的 content lambda），
     * 而下游真正需要这个高频数据的只有打字机的「目标长度」。
     */
    val streamingText: StateFlow<String?> = _streaming.asStateFlow()

    /**
     * 是否正处于**流式生成**中。UI 用它判断最后一条气泡要不要走打字机。
     *
     * 不能用 [sending] 代替：`updateAnswerFromDiscussion()` 也会把 sending 置真，
     * 那时最后一条是已落库的历史消息，若被当成流式就会整条重新“打”一遍。
     */
    private val _streamingActive = MutableStateFlow(false)
    val streamingActive: StateFlow<Boolean> = _streamingActive.asStateFlow()

    /**
     * 供 UI 渲染的会话内容：已落库消息 + 是否要显示流式气泡。
     *
     * 两者必须放在**同一个对象里原子更新**。拆成两个 StateFlow 的话，流式结束那一瞬间
     * Compose 可能只看到其中一个：先看到「不显示流式气泡」就会让回答闪一下消失，
     * 先看到「消息已落库」又会短暂出现两份。
     *
     * 另一个关键点是 [ChatContent] 是 data class：[_streaming] 每收到一个 chunk 都会让
     * combine 重算一次，但只要落库消息和显示标记都没变，算出来的对象就与上一个相等，
     * StateFlow 基于相等性的去重会把它丢掉——**不会有额外发射，也就不会有重组**。
     */
    val content: StateFlow<ChatContent> =
        combine(persistedMessages, _streaming) { db, streaming ->
            // 流式文本已经落库（与最后一条内容一致）就不必再单独显示，避免重复。
            val alreadyPersisted =
                db.lastOrNull()?.let { it.role == Role.ASSISTANT && it.content == streaming } == true
            ChatContent(messages = db, showStreamingTail = streaming != null && !alreadyPersisted)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ChatContent())

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
            // 显式切到 Default：默认会继承 viewModelScope 的 Main.immediate，
            // 那样每次发布的 builder.toString()（整段累计 O(n²) 次字符复制）都压在主线程上。
            val pump = launch(Dispatchers.Default) {
                var lastPublishNanos = 0L
                while (true) {
                    // 先挂起等第一个增量；拿到后把通道里剩下的全部排干，合并为一次发布。
                    val first = deltas.receiveCatching().getOrNull() ?: break
                    builder.append(first)
                    while (true) builder.append(deltas.tryReceive().getOrNull() ?: break)

                    // 发布节流：UI 侧打字机最快也就每 2 帧取一次长度，按 SSE 频率发布纯属浪费。
                    val waitNanos = PUBLISH_INTERVAL_NANOS - (System.nanoTime() - lastPublishNanos)
                    if (lastPublishNanos != 0L && waitNanos > 0) {
                        delay(waitNanos / 1_000_000)
                        // 睡这一会儿新到的增量一起带走，合并成同一次发布。
                        while (true) builder.append(deltas.tryReceive().getOrNull() ?: break)
                    }
                    lastPublishNanos = System.nanoTime()
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
     * 已预热到的消息条数。只被 [persistedMessages] 那条 flow 访问（`flatMapLatest` 会取消上一个
     * 收集器，所以不会并发），因此不加锁。
     */
    private var prewarmedCount = 0

    /**
     * 在消息切到 UI 前，后台预解析该会话所有 AI 回复的 Markdown 并写入 [MarkdownStateCache]，
     * 让 [com.jk.offermate.ui.components.MarkdownText] 首次组合即命中缓存，跳过空白帧。
     *
     * 只预热正文非空的 AI 消息；用户消息是纯文本，不走 Markdown 渲染。
     *
     * **只处理新增的那几条**：消息是只追加的，每次列表更新都把整段历史重扫一遍属于纯浪费——
     * 长会话下那是「消息数 × 块数」次哈希查找，而每轮对话结束都会触发一次。
     */
    private suspend fun prewarmMarkdown(messages: List<ChatMessage>) {
        // 兜底：列表意外变短（如删除消息）就从头重来，避免漏热。
        if (messages.size < prewarmedCount) prewarmedCount = 0
        for (i in prewarmedCount until messages.size) {
            val message = messages[i]
            if (message.role == Role.ASSISTANT && message.content.isNotBlank()) {
                warmBlocks(message.content)
            }
        }
        prewarmedCount = messages.size
    }

    /**
     * 按**块**预热，而不是整条消息。
     *
     * UI 侧 AI 消息已按 Markdown 块展开成多个 LazyColumn item（见 ChatRows），
     * 渲染时的缓存 key 是单个块的文本，所以预热也必须按块来，否则一条都命中不上。
     */
    private suspend fun warmBlocks(content: String) {
        StreamingMarkdown.blocksMemo(content).forEach { block ->
            if (block.isBlank()) return@forEach
            // 段落块（占多数）走轻量渲染路径，缓存的是 AnnotatedString 而不是 AST，
            // 预热必须走对应的那一条，否则 UI 首次组合时还是要同步解析一遍。
            // annotate 顺便把「这块不是段落」的判定也缓存下来，UI 侧连判定都不用重做。
            if (InlineMarkdown.annotate(block) == null) MarkdownStateCache.warm(block)
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
        // 与模型标题清洗使用同一上限，避免模型调用失败时标题异常变短。
        val title = summary ?: userText.trim().take(FollowUpService.TITLE_MAX_LENGTH)
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

        /**
         * 流式文本发布的最小间隔。见 [streamBatched] 的节流说明。
         *
         * 对齐打字机的**基准**出字间隔（`Typewriter.TARGET_EMIT_INTERVAL_MS`），而不是一帧的时长：
         * 下游只用这个值推进「目标长度」，发布得比出字还密，多出来的那些除了每次全量
         * `builder.toString()`（长回复累计 O(n²) 字节拷贝 + 同规模垃圾）之外什么也没带来。
         * 低端机上这份 GC 压力本身就是掉帧来源。
         *
         * 不必跟着打字机的自适应降频一起往上抬：发布快于消费只是有点浪费，慢于消费会让文字发涩。
         */
        private const val PUBLISH_INTERVAL_NANOS = 32_000_000L

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
