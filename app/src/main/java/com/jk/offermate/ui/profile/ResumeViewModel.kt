package com.jk.offermate.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.jk.offermate.data.ai.ResumeProfile
import com.jk.offermate.data.resume.ResumeRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 简历录入 ViewModel。仅依赖 [ResumeRepository]。
 */
class ResumeViewModel(
    private val repository: ResumeRepository
) : ViewModel() {

    val profile: StateFlow<ResumeProfile> = repository.profile.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ResumeProfile(targetRole = "")
    )

    fun save(targetRole: String, skillsCsv: String, rawText: String) {
        viewModelScope.launch { repository.save(targetRole, skillsCsv, rawText) }
    }

    companion object {
        fun provideFactory(repository: ResumeRepository) = viewModelFactory {
            initializer { ResumeViewModel(repository) }
        }
    }
}
