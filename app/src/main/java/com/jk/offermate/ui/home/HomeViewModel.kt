package com.jk.offermate.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.jk.offermate.data.resume.ResumeRepository
import com.jk.offermate.data.settings.SettingsRepository
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
    private val resumeRepository: ResumeRepository,
    private val settingsRepository: SettingsRepository
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
        val input = _uiState.value.linkInput.trim()
        if (input.isEmpty()) return
        // 用户可能粘贴的是"标题 + 链接 + 推广文案"整段分享文本（如小红书分享），
        // 而非纯链接；这里统一用 ShareIntentParser 提取出真正的链接，避免把整段文本当 URL 打开失败。
        val link = ShareIntentParser.extractLink(input) ?: input
        viewModelScope.launch {
            if (!canAnalyze()) return@launch
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
            if (!canAnalyze()) return@launch
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
            if (!canAnalyze()) return@launch
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

    fun onConsumeToast() {
        _uiState.update { it.copy(toast = null) }
    }

    /**
     * 分析前置校验：只要配置了 API Key 即可分析。
     * 未配置 Key 直接拦截；已配置但未上传简历时，仍继续分析，仅弹 Toast 提示（简历匹配度将不可用）。
     */
    private suspend fun canAnalyze(): Boolean {
        val configured = settingsRepository.activeConfig.first().isConfigured
        if (!configured) {
            _uiState.update { it.copy(message = "请先在设置中配置 API Key") }
            return false
        }
        val hasResume = resumeRepository.profile.first().rawText.isNotBlank()
        if (!hasResume) {
            _uiState.update { it.copy(toast = "未上传简历，将按无简历模式分析（简历匹配度不可用）") }
        }
        return true
    }

    companion object {
        fun provideFactory(
            postRepository: PostRepository,
            importScheduler: ImportScheduler,
            resumeRepository: ResumeRepository,
            settingsRepository: SettingsRepository
        ) = viewModelFactory {
            initializer { HomeViewModel(postRepository, importScheduler, resumeRepository, settingsRepository) }
        }
    }
}
