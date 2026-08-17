package com.jk.offermate.agent

/**
 * AI 分析流水线：编排"抽题 → 相关性筛选 → 作答"三步。
 *
 * 通过组合三个可独立测试的组件实现，各组件仅依赖 [AiClient] 抽象。
 */
class AnalysisPipeline(
    private val extractor: QuestionExtractor,
    private val matcher: RelevanceMatcher,
    private val answerer: AnswerGenerator,
    private val relevanceThreshold: Int = RelevanceMatcher.DEFAULT_THRESHOLD
) {

    /**
     * 对一段面经正文进行完整分析。
     * @return 与简历相关的、已作答的题目；若无题目或无相关题目则返回空列表。
     */
    suspend fun analyze(postText: String, profile: ResumeProfile): List<AnsweredQuestion> {
        val questions = extractor.extract(postText)
        if (questions.isEmpty()) return emptyList()

        val relevant = matcher.match(questions, profile, relevanceThreshold)
        if (relevant.isEmpty()) return emptyList()

        return answerer.answer(relevant, profile)
    }
}
