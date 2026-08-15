package com.jk.offermate.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.jk.offermate.data.settings.AiProvider
import com.jk.offermate.data.settings.AppSettings
import com.jk.offermate.data.settings.ProviderConfig
import com.jk.offermate.data.settings.SettingsRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * @param selectedProviderId 设置页当前查看/编辑的服务商
 * @param activeProviderId    当前实际启用的服务商
 * @param config              选中服务商的已保存配置
 */
data class SettingsUiState(
    val selectedProviderId: String = AiProvider.DEEPSEEK.id,
    val activeProviderId: String = AiProvider.DEEPSEEK.id,
    val config: ProviderConfig = ProviderConfig.defaultsFor(AiProvider.DEEPSEEK),
    val relevanceThreshold: Int = AppSettings.DEFAULT_THRESHOLD
)

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModel(
    private val repository: SettingsRepository
) : ViewModel() {

    private val selectedProviderId = MutableStateFlow(AiProvider.DEEPSEEK.id)

    init {
        // 初始选中当前启用的服务商
        viewModelScope.launch { selectedProviderId.value = repository.activeProviderId.first() }
    }

    val uiState: StateFlow<SettingsUiState> =
        combine(
            selectedProviderId.flatMapLatest { repository.config(it) },
            repository.activeProviderId,
            repository.relevanceThreshold,
            selectedProviderId
        ) { config, activeId, threshold, selectedId ->
            SettingsUiState(
                selectedProviderId = selectedId,
                activeProviderId = activeId,
                config = config,
                relevanceThreshold = threshold
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    fun onSelectProvider(provider: AiProvider) {
        selectedProviderId.value = provider.id
    }

    /** 启用当前选中服务商（保存 Key/模型/接口地址并设为生效）。 */
    fun onEnable(apiKey: String, model: String, baseUrl: String) {
        viewModelScope.launch {
            repository.enableProvider(selectedProviderId.value, apiKey, model, baseUrl)
        }
    }

    fun onThresholdChange(value: Int) {
        viewModelScope.launch { repository.updateRelevanceThreshold(value) }
    }

    companion object {
        fun provideFactory(repository: SettingsRepository) = viewModelFactory {
            initializer { SettingsViewModel(repository) }
        }
    }
}
