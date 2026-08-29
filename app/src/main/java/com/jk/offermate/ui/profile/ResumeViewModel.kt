package com.jk.offermate.ui.profile

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.jk.offermate.agent.resume.ResumeProfile
import com.jk.offermate.data.resume.ResumeFileStore
import com.jk.offermate.data.resume.ResumeRepository
import com.jk.offermate.data.resume.ResumeTextExtractor
import com.jk.offermate.work.ImportScheduler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 简历 ViewModel：导入 PDF → 复制到本地（供预览）+ 端侧解析文本 → 入队 AI 分析 Worker。
 * AI 结构化落地为记忆通过 [com.jk.offermate.work.AnalyzeResumeWorker] 在后台完成，
 * 支持退出设置页继续运行，以及在补配置 API Key 后自动重新触发。
 */
class ResumeViewModel(
    private val repository: ResumeRepository,
    private val extractor: ResumeTextExtractor,
    private val fileStore: ResumeFileStore,
    private val importScheduler: ImportScheduler
) : ViewModel() {

    val profile: StateFlow<ResumeProfile> = repository.profile.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ResumeProfile(targetRole = "")
    )

    val resumeFilePath: StateFlow<String?> = repository.resumeFilePath.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = null
    )

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    fun onPdfPicked(uri: Uri) {
        viewModelScope.launch {
            _loading.value = true
            _error.value = null
            try {
                val path = fileStore.copyToInternal(uri)
                repository.setFilePath(path)
                val text = extractor.extractText(uri)
                repository.updateRawText(text)
                scheduleAnalysis()
            } catch (e: Exception) {
                _error.value = e.message ?: "简历解析失败，请重试"
            } finally {
                _loading.value = false
            }
        }
    }

    /** 用户编辑识别文本后手动保存；同时重新触发 AI 分析。 */
    fun saveRawText(text: String) {
        viewModelScope.launch {
            repository.updateRawText(text)
            scheduleAnalysis()
        }
    }

    /** 持久化待分析标记并将 Worker 加入 WorkManager 队列。 */
    private suspend fun scheduleAnalysis() {
        repository.setNeedsAiAnalysis(true)
        importScheduler.enqueueResumeAnalysis()
    }

    fun consumeError() { _error.value = null }

    companion object {
        fun provideFactory(
            repository: ResumeRepository,
            extractor: ResumeTextExtractor,
            fileStore: ResumeFileStore,
            importScheduler: ImportScheduler
        ) = viewModelFactory {
            initializer { ResumeViewModel(repository, extractor, fileStore, importScheduler) }
        }
    }
}
