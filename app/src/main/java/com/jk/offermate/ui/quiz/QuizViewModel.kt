package com.jk.offermate.ui.quiz

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.jk.offermate.agent.AnsweredQuestion
import com.jk.offermate.agent.Difficulty
import com.jk.offermate.data.repository.CategoryRepository
import com.jk.offermate.data.repository.QuestionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** 一个分类的汇总：题目总数与已刷数。 */
data class CategorySummary(val name: String, val total: Int, val practiced: Int) {
    val ratio: Float get() = if (total == 0) 0f else practiced.toFloat() / total
}

data class QuizOverviewState(
    val categories: List<CategorySummary> = emptyList(),
    /** 当前搜索词。 */
    val query: String = "",
    /** 搜索命中的题目（query 非空时才有值）。 */
    val results: List<AnsweredQuestion> = emptyList()
) {
    val searching: Boolean get() = query.isNotBlank()
}

/**
 * 题库总览 ViewModel：按题目首个考点标签分类，统计每类已刷占比；
 * 合并用户手动创建的分类（可能暂无题目），并支持新增分类/手动题目。
 */
class QuizViewModel(
    private val questionRepository: QuestionRepository,
    private val categoryRepository: CategoryRepository
) : ViewModel() {

    private val query = MutableStateFlow("")

    val uiState: StateFlow<QuizOverviewState> =
        combine(
            questionRepository.observeAll(),
            categoryRepository.observeCategories(),
            query
        ) { questions, userCategories, q ->
            val groups = questions.groupBy { CategoryResolver.displayCategory(it) }
            val fromQuestions = groups.map { (name, qs) ->
                CategorySummary(name = name, total = qs.size, practiced = qs.count { it.practiced })
            }
            // 用户创建但暂无题目的分类，补成 0/0
            val emptyOnes = userCategories
                .filter { it !in groups.keys }
                .map { CategorySummary(name = it, total = 0, practiced = 0) }

            val keyword = q.trim()
            val results = if (keyword.isEmpty()) {
                emptyList()
            } else {
                questions.filter { question ->
                    question.question.contains(keyword, ignoreCase = true) ||
                        CategoryResolver.displayCategory(question).contains(keyword, ignoreCase = true) ||
                        question.tags.any { it.contains(keyword, ignoreCase = true) }
                }
            }

            QuizOverviewState(
                categories = (fromQuestions + emptyOnes).sortedByDescending { it.total },
                query = q,
                results = results
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), QuizOverviewState())

    fun onQueryChange(value: String) {
        query.value = value
    }

    fun addCategory(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch { categoryRepository.addCategory(trimmed) }
    }

    fun addManualQuestion(question: String, answer: String, category: String, difficulty: Difficulty) {
        if (question.isBlank()) return
        viewModelScope.launch {
            questionRepository.addManualQuestion(question, answer, category, difficulty)
        }
    }

    companion object {
        fun provideFactory(
            questionRepository: QuestionRepository,
            categoryRepository: CategoryRepository
        ) = viewModelFactory {
            initializer { QuizViewModel(questionRepository, categoryRepository) }
        }
    }
}
