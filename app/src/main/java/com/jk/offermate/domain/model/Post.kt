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
    val pinned: Boolean = false,
    /** 导入状态。UI 据此决定是否展示失败原因与重试入口。 */
    val status: ImportStatus = ImportStatus.DONE,
    /** 终态失败的原因，仅在 [status] 为失败/需手动粘贴时有值。 */
    val failureReason: String? = null,
    /** 原始链接。手动粘贴的记录没有真实链接，此时不提供"重试"（无正文可重跑）。 */
    val sourceUrl: String = ""
) {
    /** 可一键重试：处于失败态且有真实链接可重新读取。 */
    val canRetry: Boolean
        get() = (status == ImportStatus.FAILED || status == ImportStatus.NEEDS_MANUAL_INPUT) &&
            sourceUrl.startsWith("http")
}
