package com.jk.offermate.data.ai

/** 题目难度。 */
enum class Difficulty {
    EASY, MEDIUM, HARD, UNKNOWN;

    companion object {
        fun from(value: String?): Difficulty = when (value?.trim()?.lowercase()) {
            "easy", "简单", "低" -> EASY
            "medium", "中等", "中" -> MEDIUM
            "hard", "困难", "高" -> HARD
            else -> UNKNOWN
        }
    }
}

/** 题目来源：AI 抽取 or 用户手动添加。 */
enum class QuestionSource {
    AI, MANUAL;

    companion object {
        fun from(value: String?): QuestionSource =
            runCatching { valueOf(value.orEmpty()) }.getOrDefault(AI)
    }
}

/**
 * 已作答的题目（分析流水线的最终产物）。
 */
data class AnsweredQuestion(
    val question: String,
    val answer: String,
    val tags: List<String> = emptyList(),
    val difficulty: Difficulty = Difficulty.UNKNOWN,
    val keyPoints: List<String> = emptyList(),
    val relevanceScore: Int = 0,
    val relevanceReason: String = "",
    /** 题目唯一 id（落库后由 Room 提供；分析流水线阶段为空）。 */
    val id: String = "",
    /** 是否已刷（掌握）。 */
    val practiced: Boolean = false,
    /** 来源：AI 抽取或用户手动添加。 */
    val source: QuestionSource = QuestionSource.AI,
    /** 归属分类（由 LLM 结合已有分类决定，可新建）。为空时回退到本地启发式归并。 */
    val category: String = ""
)
