package com.jk.offermate.domain.model

/** 面经来源平台。 */
enum class Platform { NOWCODER, XIAOHONGSHU }

/** 帖子上的徽章（与设计稿对应）。 */
sealed interface PostBadge {
    /** 与简历匹配度，如 95%。 */
    data class ResumeMatch(val percent: Int) : PostBadge

    /** 文案标签，如 "高频必背"。 */
    data class Label(val text: String) : PostBadge
}

/**
 * 一篇已导入/已解析的面经帖子（领域模型，不含任何 UI 或框架细节）。
 */
data class Post(
    val id: String,
    val platform: Platform,
    val title: String,
    val summary: String,
    val timeLabel: String,
    val category: String,
    val parsedQuestionCount: Int,
    val badge: PostBadge? = null,
    val pinned: Boolean = false
)
