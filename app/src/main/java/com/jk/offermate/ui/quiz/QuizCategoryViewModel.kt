package com.jk.offermate.ui.quiz

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.jk.offermate.agent.AnsweredQuestion
import com.jk.offermate.data.repository.CategoryRepository
import com.jk.offermate.data.repository.QuestionRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 某分类下的题目 + 标记已刷 + 移动分类。
 */
class QuizCategoryViewModel(
    private val questionRepository: QuestionRepository,
    private val categoryRepository: CategoryRepository,
    val category: String
) : ViewModel() {

    val questions: StateFlow<List<AnsweredQuestion>> =
        questionRepository.observeAll().map { list ->
            list.filter { CategoryResolver.displayCategory(it) == category }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 可选分类名（用户分类 + 已有题目的显示分类去重排序），供“移动分类”弹窗选择。 */
    val categories: StateFlow<List<String>> =
        combine(
            questionRepository.observeAll(),
            categoryRepository.observeCategories()
        ) { qs, userCats ->
            (qs.map { CategoryResolver.displayCategory(it) } + userCats)
                .filter { it.isNotBlank() }
                .distinct()
                .sorted()
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun togglePracticed(q: AnsweredQuestion) {
        if (q.id.isBlank()) return
        viewModelScope.launch { questionRepository.setPracticed(q.id, !q.practiced) }
    }

    fun deleteQuestion(q: AnsweredQuestion) {
        if (q.id.isBlank()) return
        viewModelScope.launch { questionRepository.deleteQuestion(q.id) }
    }

    /** 把题目移动到另一分类；新分类不存在时顺带登记为用户分类。 */
    fun changeCategory(q: AnsweredQuestion, newCategory: String) {
        val target = newCategory.trim()
        if (q.id.isBlank() || target.isEmpty() || target == category) return
        viewModelScope.launch {
            categoryRepository.addCategory(target)
            questionRepository.updateCategory(q.id, target)
        }
    }

    companion object {
        fun provideFactory(
            questionRepository: QuestionRepository,
            categoryRepository: CategoryRepository,
            category: String
        ) = viewModelFactory {
            initializer { QuizCategoryViewModel(questionRepository, categoryRepository, category) }
        }
    }
}
