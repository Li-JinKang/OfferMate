package com.jk.offermate.ui.questions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.jk.offermate.data.ai.AnsweredQuestion
import com.jk.offermate.data.repository.QuestionRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 题目页 ViewModel：订阅某帖子的题目，并支持标记已刷。
 */
class QuestionsViewModel(
    private val questionRepository: QuestionRepository,
    postId: String
) : ViewModel() {

    val questions: StateFlow<List<AnsweredQuestion>> =
        questionRepository.observeByPost(postId).stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    fun togglePracticed(q: AnsweredQuestion) {
        if (q.id.isBlank()) return
        viewModelScope.launch { questionRepository.setPracticed(q.id, !q.practiced) }
    }

    companion object {
        fun provideFactory(questionRepository: QuestionRepository, postId: String) = viewModelFactory {
            initializer { QuestionsViewModel(questionRepository, postId) }
        }
    }
}
