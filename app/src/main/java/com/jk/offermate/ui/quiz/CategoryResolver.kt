package com.jk.offermate.ui.quiz

import com.jk.offermate.agent.pipeline.AnsweredQuestion

/**
 * 题库分组用的分类解析：分类由 LLM（[com.jk.offermate.agent.QuestionCategorizer]）决定并写入
 * [AnsweredQuestion.category]。这里只负责取值与兜底——**不再有任何写死的关键词表**：
 * 有 LLM 分类就用它；没有（旧数据/离线/未配置 Key）则回退到题目的首个标签，再不行归入"其他"。
 */
object CategoryResolver {

    const val OTHER = "其他"

    fun displayCategory(question: AnsweredQuestion): String {
        val assigned = question.category.trim()
        if (assigned.isNotEmpty()) return assigned
        return question.tags.firstOrNull()?.trim()?.takeIf { it.isNotEmpty() } ?: OTHER
    }
}
