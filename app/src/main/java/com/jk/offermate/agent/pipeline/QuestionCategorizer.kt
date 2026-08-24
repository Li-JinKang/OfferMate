package com.jk.offermate.agent.pipeline

/**
 * 题目归类策略（Strategy port）：结合"已有分类清单"为每道题决定归属类目，允许新建。
 *
 * 抽象成接口便于替换实现：当前为 LLM 决策（[CategoryClassifier]），
 * 将来可换成 embedding 聚类或规则+LLM 混合，而调用方无需改动。
 */
interface QuestionCategorizer {
    suspend fun categorize(
        questions: List<AnsweredQuestion>,
        existingCategories: List<String>
    ): List<AnsweredQuestion>
}
