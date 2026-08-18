package com.jk.offermate.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.jk.offermate.data.resume.ResumeRepository
import com.jk.offermate.domain.repository.PostRepository
import com.jk.offermate.share.ShareIntentParser
import com.jk.offermate.ui.home.HomeUiState.Companion.ALL
import com.jk.offermate.work.ImportScheduler
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 首页 ViewModel。提取/粘贴 → 入队后台任务；列表订阅 Room 真实数据。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel(
    private val postRepository: PostRepository,
    private val importScheduler: ImportScheduler,
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

    fun onToggleManualPaste() {
        _uiState.update { it.copy(manualPasteVisible = !it.manualPasteVisible) }
    }

    fun onExtract() {
        val link = _uiState.value.linkInput.trim()
        if (link.isEmpty()) return
        viewModelScope.launch {
            if (!hasResume()) return@launch
            importScheduler.enqueueUrl(link)
            _uiState.update {
                it.copy(
                    linkInput = "",
                    manualPasteVisible = false,
                    message = "已加入后台分析，完成后会通知你，可在下方列表查看"
                )
            }
        }
    }

    fun onPasteAnalyze(text: String) {
        if (text.isBlank()) return
        viewModelScope.launch {
            if (!hasResume()) return@launch
            importScheduler.enqueueText(text, _uiState.value.linkInput.trim())
            _uiState.update {
                it.copy(manualPasteVisible = false, message = "已加入后台分析，完成后会通知你")
            }
        }
    }

    /**
     * 处理从系统分享（其他 App 的"分享到 OfferMate"）收到的文本。
     * 能提取出链接则按链接入队分析；否则把整段文本当作手动粘贴的正文入队。
     */
    fun onSharedTextReceived(sharedText: String) {
        val link = ShareIntentParser.extractLink(sharedText)
        viewModelScope.launch {
            if (!hasResume()) return@launch
            if (link != null) {
                importScheduler.enqueueUrl(link)
            } else {
                importScheduler.enqueueText(sharedText)
            }
            _uiState.update {
                it.copy(message = "已收到分享内容，加入后台分析，完成后会通知你")
            }
        }
    }

    fun onTogglePin(post: com.jk.offermate.domain.model.Post) {
        viewModelScope.launch { postRepository.setPinned(post.id, !post.pinned) }
    }

    fun onDelete(postId: String) {
        viewModelScope.launch { postRepository.delete(postId) }
    }

    private suspend fun hasResume(): Boolean {
        val ok = resumeRepository.profile.first().rawText.isNotBlank()
        if (!ok) _uiState.update { it.copy(message = "请先在\"我的\"里上传简历") }
        return ok
    }

    companion object {
        fun provideFactory(
            postRepository: PostRepository,
            importScheduler: ImportScheduler,
            resumeRepository: ResumeRepository
        ) = viewModelFactory {
            initializer { HomeViewModel(postRepository, importScheduler, resumeRepository) }
        }
    }
}
