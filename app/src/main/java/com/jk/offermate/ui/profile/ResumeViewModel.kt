package com.jk.offermate.ui.profile

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.jk.offermate.data.ai.ResumeProfile
import com.jk.offermate.data.resume.ResumeRepository
import com.jk.offermate.data.resume.ResumeTextExtractor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 简历录入 ViewModel。支持手动填写与 PDF 导入解析。
 */
class ResumeViewModel(
    private val repository: ResumeRepository,
    private val extractor: ResumeTextExtractor
) : ViewModel() {

    val profile: StateFlow<ResumeProfile> = repository.profile.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ResumeProfile(targetRole = "")
    )

    private val _pdfText = MutableStateFlow<String?>(null)
    val pdfText: StateFlow<String?> = _pdfText.asStateFlow()

    private val _pdfLoading = MutableStateFlow(false)
    val pdfLoading: StateFlow<Boolean> = _pdfLoading.asStateFlow()

    fun save(targetRole: String, skillsCsv: String, rawText: String) {
        viewModelScope.launch { repository.save(targetRole, skillsCsv, rawText) }
    }

    fun onPdfPicked(uri: Uri) {
        viewModelScope.launch {
            _pdfLoading.value = true
            _pdfText.value = runCatching { extractor.extractText(uri) }.getOrDefault("")
            _pdfLoading.value = false
        }
    }

    fun consumePdfText() {
        _pdfText.value = null
    }

    companion object {
        fun provideFactory(repository: ResumeRepository, extractor: ResumeTextExtractor) = viewModelFactory {
            initializer { ResumeViewModel(repository, extractor) }
        }
    }
}
