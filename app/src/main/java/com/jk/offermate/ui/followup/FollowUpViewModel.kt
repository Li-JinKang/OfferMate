package com.jk.offermate.ui.followup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.jk.offermate.data.ai.AnsweredQuestion
import com.jk.offermate.data.ai.ChatMessage
import com.jk.offermate.data.ai.Role
import com.jk.offermate.data.ai.chat.FollowUpService
import com.jk.offermate.data.ai.chat.QuestionContext
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
    private val resumeRepository: ResumeRepository
) : ViewModel() {

    val question: StateFlow<AnsweredQuestion?> =
        questionRepository.observeById(questionId).stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = null
        )

    private val conversationId = MutableStateFlow<String?>(null)

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
            val q = question.filterNotNull().first()
            conversationId.value = conversationRepository.getOrCreateForQuestion(questionId, q.question)
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
            resumeRepository: ResumeRepository
        ) = viewModelFactory {
            initializer {
                FollowUpViewModel(
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
