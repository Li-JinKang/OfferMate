package com.jk.offermate.ui.home

import com.jk.offermate.domain.model.Post

/**
 * 首页（导入）不可变 UI 状态。单向数据流：ViewModel 产出，UI 只读渲染。
 */
data class HomeUiState(
    val linkInput: String = "",
    val isExtracting: Boolean = false,
    val filters: List<String> = listOf(ALL),
    val selectedFilter: String = ALL,
    val posts: List<Post> = emptyList()
) {
    companion object {
        const val ALL = "全部来源"
    }
}
