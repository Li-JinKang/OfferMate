package com.jk.offermate.agent

/**
 * 一道题与简历画像的相关性评估结果。
 *
 * @param question     被评估的题目
 * @param score        相关性 0-100
 * @param reason       相关性理由
 * @param matchedSkills 命中的技能点
 */
data class RelevanceResult(
    val question: ExtractedQuestion,
    val score: Int,
    val reason: String,
    val matchedSkills: List<String> = emptyList()
)
