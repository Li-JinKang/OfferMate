package com.jk.offermate.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.jk.offermate.data.memory.DetailKind
import com.jk.offermate.data.memory.MemoryProfileEntry
import com.jk.offermate.data.memory.MemoryStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 简历记忆管理 ViewModel（设置页）：列出记忆集，查看/编辑/删除结构化文件。
 *
 * 记忆是分层文件（[MemoryStore]），量小，整份加载进内存供 UI 直接编辑；
 * 每次写入/删除后重新加载，保持状态一致。
 */
class MemoryViewModel(private val store: MemoryStore) : ViewModel() {

    /** 一个可编辑的细节文件（项目/经历）。 */
    data class FileItem(val id: String, val kind: DetailKind, val content: String)

    /** 一份记忆集及其结构化文件的完整快照。 */
    data class ProfileDetail(
        val entry: MemoryProfileEntry,
        val overview: String,
        val projects: List<FileItem>,
        val experiences: List<FileItem>
    )

    private val _profiles = MutableStateFlow<List<ProfileDetail>>(emptyList())
    val profiles: StateFlow<List<ProfileDetail>> = _profiles.asStateFlow()

    private val _global = MutableStateFlow("")
    val global: StateFlow<String> = _global.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    init { refresh() }

    fun refresh() {
        viewModelScope.launch { load() }
    }

    private suspend fun load() {
        _loading.value = true
        try {
            _global.value = store.readGlobal().orEmpty()
            _profiles.value = store.listProfiles().map { entry ->
                ProfileDetail(
                    entry = entry,
                    overview = store.readProfileOverview(entry.id).orEmpty(),
                    projects = store.listDetails(entry.id, DetailKind.PROJECT).map {
                        FileItem(it, DetailKind.PROJECT, store.readDetail(entry.id, DetailKind.PROJECT, it).orEmpty())
                    },
                    experiences = store.listDetails(entry.id, DetailKind.EXPERIENCE).map {
                        FileItem(it, DetailKind.EXPERIENCE, store.readDetail(entry.id, DetailKind.EXPERIENCE, it).orEmpty())
                    }
                )
            }
        } finally {
            _loading.value = false
        }
    }

    fun saveOverview(profileId: String, text: String) = mutate { store.writeProfileOverview(profileId, text) }

    fun saveDetail(profileId: String, kind: DetailKind, itemId: String, text: String) =
        mutate { store.writeDetail(profileId, kind, itemId, text) }

    fun deleteDetail(profileId: String, kind: DetailKind, itemId: String) =
        mutate { store.deleteDetail(profileId, kind, itemId) }

    fun deleteProfile(profileId: String) = mutate { store.removeProfile(profileId) }

    fun saveGlobal(text: String) = mutate { store.writeGlobal(text) }

    private inline fun mutate(crossinline op: suspend () -> Unit) {
        viewModelScope.launch {
            op()
            load()
        }
    }

    companion object {
        fun provideFactory(store: MemoryStore) = viewModelFactory {
            initializer { MemoryViewModel(store) }
        }
    }
}
