package com.jk.offermate.ui.profile

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.jk.offermate.agent.ResumeProfile
import com.jk.offermate.data.memory.ResumeIngestor
import com.jk.offermate.data.resume.ResumeFileStore
import com.jk.offermate.data.resume.ResumeRepository
import com.jk.offermate.data.resume.ResumeTextExtractor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 简历 ViewModel：导入 PDF → 复制到本地（供预览）+ 端侧解析文本。
 * 不再要求手填岗位/技能；相关性/作答直接以简历文本为上下文。
 */
class ResumeViewModel(
    private val repository: ResumeRepository,
    private val extractor: ResumeTextExtractor,
    private val fileStore: ResumeFileStore,
    private val resumeIngestor: ResumeIngestor
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

    /** 简历结构化落地为记忆的进行状态（独立于导入本身）。 */
    private val _structuring = MutableStateFlow(false)
    val structuring: StateFlow<Boolean> = _structuring.asStateFlow()

    fun onPdfPicked(uri: Uri) {
        viewModelScope.launch {
            _loading.value = true
            _error.value = null
            try {
                val path = fileStore.copyToInternal(uri)
                repository.setFilePath(path)
                val text = extractor.extractText(uri)
                repository.updateRawText(text)
                ingestToMemory(text)
            } catch (e: Exception) {
                _error.value = e.message ?: "简历解析失败，请重试"
            } finally {
                _loading.value = false
            }
        }
    }

    /** 用户编辑识别文本后手动保存；同时刷新记忆。 */
    fun saveRawText(text: String) {
        viewModelScope.launch {
            repository.updateRawText(text)
            ingestToMemory(text)
        }
    }

    /**
     * 把简历文本结构化落地为分层文件记忆。best-effort：需要 AI（BYOK），
     * 失败不影响简历导入本身（例如未配置 Key），仅静默跳过。
     */
    private suspend fun ingestToMemory(text: String) {
        if (text.isBlank()) return
        _structuring.value = true
        try {
            resumeIngestor.ingest(text)
        } catch (_: Exception) {
            // 记忆结构化失败不阻断简历导入（如无 Key / 网络问题），留待下次保存重试
        } finally {
            _structuring.value = false
        }
    }

    fun consumeError() { _error.value = null }

    companion object {
        fun provideFactory(
            repository: ResumeRepository,
            extractor: ResumeTextExtractor,
            fileStore: ResumeFileStore,
            resumeIngestor: ResumeIngestor
        ) = viewModelFactory {
            initializer { ResumeViewModel(repository, extractor, fileStore, resumeIngestor) }
        }
    }
}
