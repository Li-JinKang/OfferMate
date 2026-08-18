package com.jk.offermate.ui.followup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.jk.offermate.agent.AnsweredQuestion
import com.jk.offermate.agent.ChatMessage
import com.jk.offermate.agent.Role
import com.jk.offermate.agent.chat.FollowUpService
import com.jk.offermate.agent.chat.QuestionContext
import com.jk.offermate.data.local.entity.ConversationEntity
import com.jk.offermate.data.repository.ConversationRepository
import com.jk.offermate.data.repository.QuestionRepository
import com.jk.offermate.data.resume.ResumeRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 题目追问页 ViewModel：围绕某道题的多轮讨论，并可据讨论更新该题答案。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FollowUpViewModel(
    private val questionId: String,
    private val questionRepository: QuestionRepository,
    private val conversationRepository: ConversationRepository,
    private val followUpService: FollowUpService,
    private val resumeRepository: ResumeRepository,
    /** 若指定，则打开该会话；否则取/建该题最近一次会话。 */
    private val initialConversationId: String? = null
) : ViewModel() {

    val question: StateFlow<AnsweredQuestion?> =
        questionRepository.observeById(questionId).stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = null
        )

    private val conversationId = MutableStateFlow<String?>(null)

    /** 该题下所有的追问会话（多轮讨论），用于会话切换器 UI。 */
    val conversations: StateFlow<List<ConversationEntity>> =
        conversationRepository.observeConversationsForQuestion(questionId).stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    /** 当前展示中的会话 id（用于让 UI 高亮选中的会话）。 */
    val activeConversationId: StateFlow<String?> = conversationId.asStateFlow()

    val messages: StateFlow<List<ChatMessage>> =
        conversationId
            .flatMapLatest { id ->
                if (id == null) flowOf(emptyList()) else conversationRepository.observeMessages(id)
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = emptyList()
            )

    private val _sending = MutableStateFlow(false)
    val sending: StateFlow<Boolean> = _sending.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _notice = MutableStateFlow<String?>(null)
    val notice: StateFlow<String?> = _notice.asStateFlow()

    init {
        viewModelScope.launch {
            if (initialConversationId != null) {
                conversationId.value = initialConversationId
            } else {
                val q = question.filterNotNull().first()
                conversationId.value = conversationRepository.getOrCreateForQuestion(questionId, q.question)
            }
        }
    }

    /** 发送一条追问并获取模型回复。 */
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
                _error.value = e.message ?: "追问失败，请稍后重试"
            } finally {
                _sending.value = false
            }
        }
    }

    /** 综合当前讨论，重写并保存该题答案。 */
    fun updateAnswerFromDiscussion() {
        if (_sending.value) return
        viewModelScope.launch {
            _error.value = null
            _sending.value = true
            try {
                val convId = ensureConversation()
                val history = conversationRepository.history(convId)
                if (history.isEmpty()) {
                    _error.value = "先追问几轮再更新答案吧"
                    return@launch
                }
                val revised = followUpService.reviseAnswer(
                    context = currentContext(),
                    profile = resumeRepository.profile.first(),
                    history = history
                )
                if (revised.isNotBlank()) {
                    questionRepository.updateAnswer(questionId, revised)
                    _notice.value = "答案已根据讨论更新"
                }
            } catch (e: Exception) {
                _error.value = e.message ?: "更新答案失败，请稍后重试"
            } finally {
                _sending.value = false
            }
        }
    }

    /** 另起一轮新的追问会话（不影响历史会话，可通过会话切换器随时切回去）。 */
    fun startNewSession() {
        if (_sending.value) return
        viewModelScope.launch {
            val q = question.filterNotNull().first()
            conversationId.value = conversationRepository.createNewForQuestion(questionId, q.question)
        }
    }

    /** 切换到指定的历史会话。 */
    fun switchToConversation(id: String) {
        if (_sending.value) return
        conversationId.value = id
    }

    fun consumeError() { _error.value = null }
    fun consumeNotice() { _notice.value = null }

    private suspend fun ensureConversation(): String {
        conversationId.value?.let { return it }
        val q = question.filterNotNull().first()
        return conversationRepository.getOrCreateForQuestion(questionId, q.question).also {
            conversationId.value = it
        }
    }

    private fun currentContext(): QuestionContext {
        val q = question.value
        return QuestionContext(
            question = q?.question.orEmpty(),
            currentAnswer = q?.answer.orEmpty(),
            tags = q?.tags ?: emptyList()
        )
    }

    companion object {
        fun provideFactory(
            questionId: String,
            questionRepository: QuestionRepository,
            conversationRepository: ConversationRepository,
            followUpService: FollowUpService,
            resumeRepository: ResumeRepository,
            initialConversationId: String? = null
        ) = viewModelFactory {
            initializer {
                FollowUpViewModel(
                    questionId,
                    questionRepository,
                    conversationRepository,
                    followUpService,
                    resumeRepository,
                    initialConversationId
                )
            }
        }
    }
}
