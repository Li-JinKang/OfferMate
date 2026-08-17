package com.jk.offermate.agent

/**
 * 面经分析门面（Facade port）：把"抽题 → 相关性筛选 → 作答"编排为一个用例，
 * 对外只暴露一个方法。上层（导入编排）依赖此抽象，不关心内部由几步、如何实现，
 * 也便于将来替换实现（如引入端侧 embedding 初筛）。
 */
interface PostAnalyzer {
    /**
     * 分析一段面经正文，产出与简历相关且已作答的题目。
     * @return 无题目或无相关题目时返回空列表。
     */
    suspend fun analyze(postText: String, profile: ResumeProfile): List<AnsweredQuestion>
}
