package com.jk.offermate.ui.quiz

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.jk.offermate.data.ai.AnsweredQuestion
import com.jk.offermate.data.repository.QuestionRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 某分类下的题目 + 标记已刷。
 */
class QuizCategoryViewModel(
    private val questionRepository: QuestionRepository,
    val category: String
) : ViewModel() {

    val questions: StateFlow<List<AnsweredQuestion>> =
        questionRepository.observeAll().map { list ->
            list.filter { (it.tags.firstOrNull()?.takeIf(String::isNotBlank) ?: "其他") == category }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun togglePracticed(q: AnsweredQuestion) {
        if (q.id.isBlank()) return
        viewModelScope.launch { questionRepository.setPracticed(q.id, !q.practiced) }
    }

    fun deleteQuestion(q: AnsweredQuestion) {
        if (q.id.isBlank()) return
        viewModelScope.launch { questionRepository.deleteQuestion(q.id) }
    }

    companion object {
        fun provideFactory(questionRepository: QuestionRepository, category: String) = viewModelFactory {
            initializer { QuizCategoryViewModel(questionRepository, category) }
        }
    }
}
