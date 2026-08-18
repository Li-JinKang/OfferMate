package com.jk.offermate.ui.aichat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.jk.offermate.data.repository.ConversationRepository
import com.jk.offermate.data.repository.QuestionRepository
import com.jk.offermate.ui.quiz.CategoryResolver
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/** 抽屉里的一条历史对话。 */
data class ConversationHistoryItem(
    val conversationId: String,
    val questionId: String,
    val title: String,
    val updatedAt: Long
)

/** 抽屉里"开始新对话"可选的题目。 */
data class StartCandidate(
    val id: String,
    val question: String,
    val category: String
)

data class AiChatDrawerState(
    val query: String = "",
    val history: List<ConversationHistoryItem> = emptyList(),
    val candidates: List<StartCandidate> = emptyList(),
    /** 最近一次对话（不受搜索影响），用于进入 Tab 时默认展示。 */
    val latest: ConversationHistoryItem? = null
)

/**
 * AI 对话页抽屉 ViewModel：提供「对话历史」+「可发起对话的题目」，并支持搜索过滤两者。
 * 聊天本体复用 FollowUpViewModel/FollowUpScreen，本 VM 只负责导航侧栏的数据。
 */
class AiChatViewModel(
    questionRepository: QuestionRepository,
    conversationRepository: ConversationRepository
) : ViewModel() {

    private val query = MutableStateFlow("")

    val uiState: StateFlow<AiChatDrawerState> =
        combine(
            conversationRepository.observeAllConversations(),
            questionRepository.observeAll(),
            query
        ) { conversations, questions, q ->
            val keyword = q.trim()
            val questionById = questions.associateBy { it.id }

            val allHistory = conversations
                .filter { !it.questionId.isNullOrBlank() }
                .map { c ->
                    val title = c.title.ifBlank { questionById[c.questionId]?.question ?: "对话" }
                    ConversationHistoryItem(c.id, c.questionId!!, title, c.updatedAt)
                }
            val history = if (keyword.isEmpty()) {
                allHistory
            } else {
                allHistory.filter { it.title.contains(keyword, ignoreCase = true) }
            }

            val candidates = questions
                .filter { it.id.isNotBlank() }
                .map { StartCandidate(it.id, it.question, CategoryResolver.displayCategory(it)) }
                .let { list ->
                    if (keyword.isEmpty()) list
                    else list.filter {
                        it.question.contains(keyword, ignoreCase = true) ||
                            it.category.contains(keyword, ignoreCase = true)
                    }
                }

            AiChatDrawerState(
                query = q,
                history = history,
                candidates = candidates,
                latest = allHistory.firstOrNull()
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AiChatDrawerState())

    fun onQueryChange(value: String) {
        query.value = value
    }

    companion object {
        fun provideFactory(
            questionRepository: QuestionRepository,
            conversationRepository: ConversationRepository
        ) = viewModelFactory {
            initializer {
                AiChatViewModel(questionRepository, conversationRepository)
            }
        }
    }
}
