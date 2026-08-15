package com.jk.offermate.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.jk.offermate.data.importer.ImportResult
import com.jk.offermate.data.importer.Importer
import com.jk.offermate.data.resume.ResumeRepository
import com.jk.offermate.domain.repository.PostRepository
import com.jk.offermate.ui.home.HomeUiState.Companion.ALL
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 首页 ViewModel（MVVM + 单向数据流）。依赖 [PostRepository]、[Importer]、[ResumeRepository] 抽象。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel(
    private val postRepository: PostRepository,
    private val importer: Importer,
    private val resumeRepository: ResumeRepository
) : ViewModel() {

    private val filters = listOf(ALL) + postRepository.categories()

    private val _uiState = MutableStateFlow(HomeUiState(filters = filters))
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private val selectedFilter = MutableStateFlow(ALL)

    init {
        viewModelScope.launch {
            selectedFilter
                .flatMapLatest { f -> postRepository.observePosts(if (f == ALL) null else f) }
                .collect { posts -> _uiState.update { it.copy(posts = posts) } }
        }
    }

    fun onLinkChange(value: String) {
        _uiState.update { it.copy(linkInput = value) }
    }

    fun onSelectFilter(filter: String) {
        selectedFilter.value = filter
        _uiState.update { it.copy(selectedFilter = filter) }
    }

    fun onExtract() {
        val link = _uiState.value.linkInput.trim()
        if (link.isEmpty() || _uiState.value.isExtracting) return
        launchAnalyze { profile -> importer.importFromUrl(link, profile) }
    }

    fun onPasteAnalyze(text: String) {
        if (text.isBlank() || _uiState.value.isExtracting) return
        launchAnalyze { profile -> importer.importFromText(text, profile, _uiState.value.linkInput.trim()) }
    }

    private fun launchAnalyze(block: suspend (com.jk.offermate.data.ai.ResumeProfile) -> ImportResult) {
        viewModelScope.launch {
            val profile = resumeRepository.profile.first()
            if (profile.targetRole.isBlank()) {
                _uiState.update { it.copy(message = "请先在\"我的\"里填写目标岗位/简历") }
                return@launch
            }
            _uiState.update { it.copy(isExtracting = true, message = null, results = emptyList()) }
            handle(block(profile))
        }
    }

    private fun handle(result: ImportResult) {
        _uiState.update { state ->
            when (result) {
                is ImportResult.Success -> state.copy(
                    isExtracting = false,
                    results = result.questions,
                    manualPasteVisible = false,
                    message = if (result.questions.isEmpty()) "未发现与你简历相关的题目" else null
                )
                is ImportResult.NeedsManualInput -> state.copy(
                    isExtracting = false,
                    manualPasteVisible = true,
                    message = "自动读取失败，请粘贴帖子正文后分析"
                )
                is ImportResult.Failed -> state.copy(
                    isExtracting = false,
                    message = result.reason
                )
            }
        }
    }

    companion object {
        fun provideFactory(
            postRepository: PostRepository,
            importer: Importer,
            resumeRepository: ResumeRepository
        ) = viewModelFactory {
            initializer { HomeViewModel(postRepository, importer, resumeRepository) }
        }
    }
}
