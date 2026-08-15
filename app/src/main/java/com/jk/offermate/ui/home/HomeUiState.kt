package com.jk.offermate.ui.home

import com.jk.offermate.domain.model.Post

/**
 * 首页（导入）不可变 UI 状态。列表来自 Room 真实数据；提取走后台任务。
 */
data class HomeUiState(
    val linkInput: String = "",
    val filters: List<String> = listOf(ALL),
    val selectedFilter: String = ALL,
    val posts: List<Post> = emptyList(),
    val message: String? = null,
    val manualPasteVisible: Boolean = false
) {
    companion object {
        const val ALL = "全部来源"
    }
}
