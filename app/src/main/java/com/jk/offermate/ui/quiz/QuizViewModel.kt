package com.jk.offermate.ui.quiz

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.jk.offermate.data.ai.AnsweredQuestion
import com.jk.offermate.data.repository.QuestionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/** 题库 UI 状态：分类（按首个考点标签）+ 当前筛选 + 题目列表。 */
data class QuizUiState(
    val categories: List<String> = listOf(ALL),
    val selected: String = ALL,
    val questions: List<AnsweredQuestion> = emptyList()
) {
    companion object {
        const val ALL = "全部"
    }
}

/**
 * 题库 ViewModel：聚合所有题目，按首个考点标签分类与筛选。
 */
class QuizViewModel(
    questionRepository: QuestionRepository
) : ViewModel() {

    private val selected = MutableStateFlow(QuizUiState.ALL)

    val uiState: StateFlow<QuizUiState> =
        combine(questionRepository.observeAll(), selected) { list, sel ->
            val categories = list.mapNotNull { it.tags.firstOrNull()?.takeIf(String::isNotBlank) }.distinct()
            val filtered = if (sel == QuizUiState.ALL) list else list.filter { (it.tags.firstOrNull() ?: "") == sel }
            QuizUiState(
                categories = listOf(QuizUiState.ALL) + categories,
                selected = sel,
                questions = filtered
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), QuizUiState())

    fun onSelect(category: String) {
        selected.value = category
    }

    companion object {
        fun provideFactory(questionRepository: QuestionRepository) = viewModelFactory {
            initializer { QuizViewModel(questionRepository) }
        }
    }
}
