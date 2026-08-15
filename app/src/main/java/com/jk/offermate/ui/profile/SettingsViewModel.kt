package com.jk.offermate.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.jk.offermate.data.settings.AppSettings
import com.jk.offermate.data.settings.SettingsRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * "我的/设置"页 ViewModel。仅依赖 [SettingsRepository] 抽象。
 */
class SettingsViewModel(
    private val repository: SettingsRepository
) : ViewModel() {

    val settings: StateFlow<AppSettings> = repository.settings.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AppSettings()
    )

    fun updateApiKey(key: String) {
        viewModelScope.launch { repository.updateApiKey(key) }
    }

    fun updateModel(model: String) {
        viewModelScope.launch { repository.updateModel(model) }
    }

    fun updateThreshold(value: Int) {
        viewModelScope.launch { repository.updateRelevanceThreshold(value) }
    }

    fun updateProvider(provider: com.jk.offermate.data.settings.AiProvider) {
        viewModelScope.launch { repository.updateProvider(provider) }
    }

    fun updateBaseUrl(url: String) {
        viewModelScope.launch { repository.updateBaseUrl(url) }
    }

    companion object {
        fun provideFactory(repository: SettingsRepository) = viewModelFactory {
            initializer { SettingsViewModel(repository) }
        }
    }
}
