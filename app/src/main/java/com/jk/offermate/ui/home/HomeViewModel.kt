package com.jk.offermate.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.jk.offermate.domain.repository.PostRepository
import com.jk.offermate.ui.home.HomeUiState.Companion.ALL
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn

/**
 * 首页 ViewModel（MVVM + 单向数据流）。仅依赖 [PostRepository] 抽象，便于替换与测试。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel(
    private val postRepository: PostRepository
) : ViewModel() {

    private val linkInput = MutableStateFlow("")
    private val isExtracting = MutableStateFlow(false)
    private val selectedFilter = MutableStateFlow(ALL)

    private val filters = listOf(ALL) + postRepository.categories()

    private val postsFlow = selectedFilter.flatMapLatest { filter ->
        postRepository.observePosts(if (filter == ALL) null else filter)
    }

    val uiState: StateFlow<HomeUiState> =
        combine(linkInput, isExtracting, selectedFilter, postsFlow) { link, extracting, filter, posts ->
            HomeUiState(
                linkInput = link,
                isExtracting = extracting,
                filters = filters,
                selectedFilter = filter,
                posts = posts
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = HomeUiState(filters = filters)
        )

    fun onLinkChange(value: String) {
        linkInput.value = value
    }

    fun onSelectFilter(filter: String) {
        selectedFilter.value = filter
    }

    fun onExtract() {
        // TODO(P4.2): 接入链接读取 + 分析流水线，通过 WorkManager 后台执行并落库。
        // 目前仅占位，避免误导为已联通。
    }

    companion object {
        fun provideFactory(postRepository: PostRepository) = viewModelFactory {
            initializer { HomeViewModel(postRepository) }
        }
    }
}
