package com.jk.offermate.agent.pipeline

/**
 * 从面经帖子中抽取出的一道面试题（尚未做相关性筛选/作答）。
 *
 * @param question     题目内容
 * @param tags         考点标签（如 "Android"、"算法"）
 * @param sourceSnippet 该题在原帖中的出处片段（可空）
 */
data class ExtractedQuestion(
    val question: String,
    val tags: List<String> = emptyList(),
    val sourceSnippet: String? = null
)
