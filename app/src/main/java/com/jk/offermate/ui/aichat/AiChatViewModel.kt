package com.jk.offermate.ui.aichat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.jk.offermate.agent.Difficulty
import com.jk.offermate.data.repository.ConversationRepository
import com.jk.offermate.data.repository.QuestionRepository
import com.jk.offermate.ui.quiz.CategoryResolver
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/** AI 对话页里可被选择的题目条目。 */
data class ChatQuestionItem(
    val id: String,
    val question: String,
    val category: String,
    val difficulty: Difficulty,
    val conversationCount: Int,
    val lastUpdated: Long?
) {
    val hasConversation: Boolean get() = conversationCount > 0
}

data class AiChatState(
    val query: String = "",
    val categories: List<String> = emptyList(),
    val selectedCategory: String? = null,
    /** 已聊过的题目（按最近活跃时间倒序），用于“继续对话”。 */
    val recent: List<ChatQuestionItem> = emptyList(),
    /** 经搜索/分类过滤后的全部题目。 */
    val all: List<ChatQuestionItem> = emptyList(),
    val totalCount: Int = 0
)

/**
 * AI 对话页 ViewModel：把题库、每题会话概要、分类合并成一个可搜索/筛选的选题列表。
 *
 * 题目很多时的快速定位机制：
 * 1) 顶部实时搜索（题干 / 标签 / 分类）。
 * 2) 分类筛选 chips。
 * 3) “继续对话”区把已经聊过的题按最近活跃时间置顶，覆盖最常见的再入场景。
 * 4) 全部题目沿用题库的相关度排序（relevanceScore desc）。
 */
class AiChatViewModel(
    private val questionRepository: QuestionRepository,
    conversationRepository: ConversationRepository
) : ViewModel() {

    private val query = MutableStateFlow("")
    private val selectedCategory = MutableStateFlow<String?>(null)

    val uiState: StateFlow<AiChatState> =
        combine(
            questionRepository.observeAll(),
            conversationRepository.observeConversationSummaries(),
            query,
            selectedCategory
        ) { questions, summaries, q, cat ->
            // observeAll 已按 relevanceScore desc 排序，保持该顺序
            val items = questions
                .filter { it.id.isNotBlank() }
                .map { question ->
                    val info = summaries[question.id]
                    ChatQuestionItem(
                        id = question.id,
                        question = question.question,
                        category = CategoryResolver.displayCategory(question),
                        difficulty = question.difficulty,
                        conversationCount = info?.count ?: 0,
                        lastUpdated = info?.lastUpdated
                    )
                }

            val categories = items.map { it.category }.distinct().sorted()
            val effectiveCat = cat?.takeIf { it in categories }
            val keyword = q.trim()

            val filtered = items.filter { item ->
                (effectiveCat == null || item.category == effectiveCat) &&
                    (keyword.isEmpty() || item.question.contains(keyword, ignoreCase = true) ||
                        item.category.contains(keyword, ignoreCase = true))
            }

            val recent = filtered
                .filter { it.hasConversation }
                .sortedByDescending { it.lastUpdated ?: 0L }
                .take(8)

            AiChatState(
                query = q,
                categories = categories,
                selectedCategory = effectiveCat,
                recent = recent,
                all = filtered,
                totalCount = items.size
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AiChatState())

    fun onQueryChange(value: String) {
        query.value = value
    }

    fun onSelectCategory(category: String?) {
        selectedCategory.value = category
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
