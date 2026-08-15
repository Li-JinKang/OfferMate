package com.jk.offermate.ui.quiz

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.jk.offermate.data.ai.AnsweredQuestion
import com.jk.offermate.data.repository.QuestionRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

/**
 * 题库 ViewModel：聚合所有帖子的题目。
 */
class QuizViewModel(
    questionRepository: QuestionRepository
) : ViewModel() {

    val questions: StateFlow<List<AnsweredQuestion>> =
        questionRepository.observeAll().stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    companion object {
        fun provideFactory(questionRepository: QuestionRepository) = viewModelFactory {
            initializer { QuizViewModel(questionRepository) }
        }
    }
}
