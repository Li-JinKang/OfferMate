package com.jk.offermate.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.jk.offermate.agent.mcp.McpServerConfig
import com.jk.offermate.agent.mcp.McpToolRepository
import com.jk.offermate.data.resume.ResumeRepository
import com.jk.offermate.data.settings.AiProvider
import com.jk.offermate.data.settings.AppSettings
import com.jk.offermate.data.settings.ProviderConfig
import com.jk.offermate.data.settings.SettingsRepository
import com.jk.offermate.work.ImportScheduler
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
 * @param mcpServers          已配置的外部 MCP 服务器
 * @param mcpToolCount        最近一次发现的 MCP 工具数
 */
data class SettingsUiState(
    val selectedProviderId: String = AiProvider.DEEPSEEK.id,
    val activeProviderId: String = AiProvider.DEEPSEEK.id,
    val config: ProviderConfig = ProviderConfig.defaultsFor(AiProvider.DEEPSEEK),
    val relevanceThreshold: Int = AppSettings.DEFAULT_THRESHOLD,
    val mcpServers: List<McpServerConfig> = emptyList(),
    val mcpToolCount: Int = 0,
    val mcpRefreshing: Boolean = false
)

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModel(
    private val repository: SettingsRepository,
    private val mcpToolRepository: McpToolRepository? = null,
    private val resumeRepository: ResumeRepository? = null,
    private val importScheduler: ImportScheduler? = null
) : ViewModel() {

    private val selectedProviderId = MutableStateFlow(AiProvider.DEEPSEEK.id)
    private val mcpToolCount = MutableStateFlow(mcpToolRepository?.current()?.size ?: 0)
    private val mcpRefreshing = MutableStateFlow(false)

    init {
        // 初始选中当前启用的服务商
        viewModelScope.launch { selectedProviderId.value = repository.activeProviderId.first() }
    }

    val uiState: StateFlow<SettingsUiState> =
        combine(
            selectedProviderId.flatMapLatest { repository.config(it) },
            repository.activeProviderId,
            repository.relevanceThreshold,
            selectedProviderId,
            combine(repository.mcpServers, mcpToolCount, mcpRefreshing) { servers, count, refreshing ->
                Triple(servers, count, refreshing)
            }
        ) { config, activeId, threshold, selectedId, mcp ->
            SettingsUiState(
                selectedProviderId = selectedId,
                activeProviderId = activeId,
                config = config,
                relevanceThreshold = threshold,
                mcpServers = mcp.first,
                mcpToolCount = mcp.second,
                mcpRefreshing = mcp.third
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    fun onSelectProvider(provider: AiProvider) {
        selectedProviderId.value = provider.id
    }

    /** 启用当前选中服务商（保存 Key/模型/接口地址并设为生效）。若有待分析简历则重新触发。 */
    fun onEnable(apiKey: String, model: String, baseUrl: String) {
        viewModelScope.launch {
            repository.enableProvider(selectedProviderId.value, apiKey, model, baseUrl)
            if (apiKey.isNotBlank() && resumeRepository?.needsAiAnalysis?.first() == true) {
                importScheduler?.enqueueResumeAnalysis()
            }
        }
    }

    fun onThresholdChange(value: Int) {
        viewModelScope.launch { repository.updateRelevanceThreshold(value) }
    }

    fun onAddMcpServer(name: String, url: String) {
        viewModelScope.launch {
            repository.addMcpServer(name, url)
            refreshMcpTools()
        }
    }

    fun onRemoveMcpServer(name: String) {
        viewModelScope.launch {
            repository.removeMcpServer(name)
            refreshMcpTools()
        }
    }

    fun onToggleMcpServer(name: String, enabled: Boolean) {
        viewModelScope.launch {
            repository.setMcpServerEnabled(name, enabled)
            refreshMcpTools()
        }
    }

    /** 手动重连所有 MCP 服务器、刷新可用工具。 */
    fun onRefreshMcp() {
        viewModelScope.launch { refreshMcpTools() }
    }

    private suspend fun refreshMcpTools() {
        val repo = mcpToolRepository ?: return
        mcpRefreshing.value = true
        mcpToolCount.value = runCatching { repo.refresh() }.getOrDefault(repo.current().size)
        mcpRefreshing.value = false
    }

    companion object {
        fun provideFactory(
            repository: SettingsRepository,
            mcpToolRepository: McpToolRepository? = null,
            resumeRepository: ResumeRepository? = null,
            importScheduler: ImportScheduler? = null
        ) = viewModelFactory {
            initializer { SettingsViewModel(repository, mcpToolRepository, resumeRepository, importScheduler) }
        }
    }
}
