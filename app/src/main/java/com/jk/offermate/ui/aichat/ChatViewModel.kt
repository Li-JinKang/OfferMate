package com.jk.offermate.ui.aichat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.jk.offermate.agent.AnsweredQuestion
import com.jk.offermate.agent.ChatMessage
import com.jk.offermate.agent.Role
import com.jk.offermate.agent.chat.FollowUpService
import com.jk.offermate.agent.chat.QuestionContext
import com.jk.offermate.data.repository.ConversationRepository
import com.jk.offermate.data.repository.QuestionRepository
import com.jk.offermate.data.resume.ResumeRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
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
    private val followUpService: FollowUpService,
    private val resumeRepository: ResumeRepository
) : ViewModel() {

    /** 当前会话 id；null 表示尚未创建（空白对话）。 */
    private val conversationId = MutableStateFlow(initialConversationId)

    /** 绑定的题目（自由对话为 null）。 */
    val question: StateFlow<AnsweredQuestion?> =
        (if (questionId == null) flowOf(null) else questionRepository.observeById(questionId))
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val messages: StateFlow<List<ChatMessage>> =
        conversationId
            .flatMapLatest { id ->
                if (id == null) flowOf(emptyList()) else conversationRepository.observeMessages(id)
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _sending = MutableStateFlow(false)
    val sending: StateFlow<Boolean> = _sending.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _notice = MutableStateFlow<String?>(null)
    val notice: StateFlow<String?> = _notice.asStateFlow()

    fun send(text: String) {
        val content = text.trim()
        if (content.isEmpty() || _sending.value) return
        viewModelScope.launch {
            _error.value = null
            _sending.value = true
            try {
                val convId = ensureConversation()
                conversationRepository.append(convId, Role.USER, content)
                val reply = followUpService.reply(
                    context = currentContext(),
                    profile = resumeRepository.profile.first(),
                    history = conversationRepository.history(convId)
                )
                conversationRepository.append(convId, Role.ASSISTANT, reply)
            } catch (e: Exception) {
                _error.value = e.message ?: "回复失败，请稍后重试"
            } finally {
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
                    profile = resumeRepository.profile.first(),
                    history = history
                )
                if (revised.isNotBlank()) {
                    questionRepository.updateAnswer(qId, revised)
                    _notice.value = "答案已根据讨论更新"
                }
            } catch (e: Exception) {
                _error.value = e.message ?: "更新答案失败，请稍后重试"
            } finally {
                _sending.value = false
            }
        }
    }

    fun consumeError() { _error.value = null }
    fun consumeNotice() { _notice.value = null }

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
            followUpService: FollowUpService,
            resumeRepository: ResumeRepository
        ) = viewModelFactory {
            initializer {
                ChatViewModel(
                    initialConversationId,
                    questionId,
                    questionRepository,
                    conversationRepository,
                    followUpService,
                    resumeRepository
                )
            }
        }
    }
}
