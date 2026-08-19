package com.jk.offermate.ui.aichat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.jk.offermate.data.repository.ConversationRepository
import com.jk.offermate.data.repository.ConversationSearchHit
import com.jk.offermate.data.repository.QuestionRepository
import com.jk.offermate.ui.quiz.CategoryResolver
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn

/** 抽屉里的一条历史对话（[questionId] 为空即自由对话）。 */
data class ConversationHistoryItem(
    val conversationId: String,
    val questionId: String?,
    val title: String,
    val updatedAt: Long,
    /** 搜索命中的消息片段（仅内容命中时有值；标题命中或非搜索态为 null）。 */
    val snippet: String? = null,
    /** 命中消息 id（用于打开会话后滚动定位；无内容命中为 null）。 */
    val hitMessageId: Long? = null
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
    val latest: ConversationHistoryItem? = null,
    /** 是否已完成首次数据加载：避免加载完成前误显示“无对话”空状态。 */
    val initialized: Boolean = false
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

    // 搜索态的历史命中走 DB LIKE（含消息内容）；null 表示非搜索态，用全部会话。
    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    private val hits: Flow<List<ConversationSearchHit>?> =
        query
            .debounce { q -> if (q.isBlank()) 0L else 250L }
            .flatMapLatest { q ->
                if (q.isBlank()) flowOf(null) else conversationRepository.searchConversations(q)
            }

    val uiState: StateFlow<AiChatDrawerState> =
        combine(
            conversationRepository.observeAllConversations(),
            questionRepository.observeAll(),
            query,
            hits
        ) { conversations, questions, q, searchHits ->
            val keyword = q.trim()
            val questionById = questions.associateBy { it.id }
            fun titleOf(title: String, questionId: String?): String =
                title.ifBlank { questionId?.let { questionById[it]?.question } ?: "新对话" }

            val allHistory = conversations.map { c ->
                ConversationHistoryItem(c.id, c.questionId, titleOf(c.title, c.questionId), c.updatedAt)
            }
            // 非搜索态用全部会话；搜索态用按内容命中的结果（带片段与命中消息 id）
            val history = if (searchHits == null) {
                allHistory
            } else {
                searchHits.map { h ->
                    ConversationHistoryItem(
                        conversationId = h.conversationId,
                        questionId = h.questionId,
                        title = titleOf(h.title, h.questionId),
                        updatedAt = h.updatedAt,
                        snippet = h.snippet,
                        hitMessageId = h.hitMessageId
                    )
                }
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
                latest = allHistory.firstOrNull(),
                initialized = true
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
