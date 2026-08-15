package com.jk.offermate.ui.home

import com.jk.offermate.data.ai.AnsweredQuestion
import com.jk.offermate.domain.model.Post

/**
 * 首页（导入）不可变 UI 状态。单向数据流：ViewModel 产出，UI 只读渲染。
 */
data class HomeUiState(
    val linkInput: String = "",
    val isExtracting: Boolean = false,
    val filters: List<String> = listOf(ALL),
    val selectedFilter: String = ALL,
    val posts: List<Post> = emptyList(),
    /** 本次分析产出的相关题目（已作答）。 */
    val results: List<AnsweredQuestion> = emptyList(),
    /** 提示/错误信息。 */
    val message: String? = null,
    /** 是否显示"手动粘贴正文"入口（自动读取失败时）。 */
    val manualPasteVisible: Boolean = false
) {
    companion object {
        const val ALL = "全部来源"
    }
}
