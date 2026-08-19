package com.jk.offermate.ui.quiz

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.jk.offermate.agent.AnsweredQuestion
import com.jk.offermate.agent.Difficulty
import com.jk.offermate.data.repository.CategoryRepository
import com.jk.offermate.data.repository.QuestionRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
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

    // 搜索命中走 DB LIKE：query 防抖后按最新词查库，不再把全表拉进内存过滤。
    // 空词立即返回（不延迟首屏），非空词防抖 250ms 避免逐键查库。
    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    private val results: Flow<List<AnsweredQuestion>> =
        query
            .debounce { q -> if (q.isBlank()) 0L else 250L }
            .flatMapLatest { q -> questionRepository.search(q) }

    val uiState: StateFlow<QuizOverviewState> =
        combine(
            questionRepository.observeAll(),
            categoryRepository.observeCategories(),
            query,
            results
        ) { questions, userCategories, q, searchResults ->
            val groups = questions.groupBy { CategoryResolver.displayCategory(it) }
            val fromQuestions = groups.map { (name, qs) ->
                CategorySummary(name = name, total = qs.size, practiced = qs.count { it.practiced })
            }
            // 用户创建但暂无题目的分类，补成 0/0
            val emptyOnes = userCategories
                .filter { it !in groups.keys }
                .map { CategorySummary(name = it, total = 0, practiced = 0) }

            QuizOverviewState(
                categories = (fromQuestions + emptyOnes).sortedByDescending { it.total },
                query = q,
                results = searchResults
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

    /**
     * 级联删除分类：删除该显示分类下的所有题目，并移除同名用户分类标签。
     * 因显示分类是「category 字段 or 首个标签 or 其他」的启发式，需在应用层筛出题目 id 再批量删。
     */
    fun deleteCategory(name: String) {
        val target = name.trim()
        if (target.isEmpty()) return
        viewModelScope.launch {
            val all = questionRepository.observeAll().first()
            val ids = all.filter { CategoryResolver.displayCategory(it) == target }.map { it.id }
            questionRepository.deleteQuestions(ids)
            categoryRepository.deleteCategory(target)
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
