package com.jk.offermate.ui.quiz

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.jk.offermate.data.repository.QuestionRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** 一个分类的汇总：题目总数与已刷数。 */
data class CategorySummary(val name: String, val total: Int, val practiced: Int) {
    val ratio: Float get() = if (total == 0) 0f else practiced.toFloat() / total
}

data class QuizOverviewState(val categories: List<CategorySummary> = emptyList())

/**
 * 题库总览 ViewModel：按题目首个考点标签分类，统计每类已刷占比。
 */
class QuizViewModel(
    questionRepository: QuestionRepository
) : ViewModel() {

    val uiState: StateFlow<QuizOverviewState> =
        questionRepository.observeAll().map { list ->
            val groups = list.groupBy { it.tags.firstOrNull()?.takeIf(String::isNotBlank) ?: "其他" }
            QuizOverviewState(
                categories = groups.map { (name, qs) ->
                    CategorySummary(name = name, total = qs.size, practiced = qs.count { it.practiced })
                }.sortedByDescending { it.total }
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), QuizOverviewState())

    companion object {
        fun provideFactory(questionRepository: QuestionRepository) = viewModelFactory {
            initializer { QuizViewModel(questionRepository) }
        }
    }
}
