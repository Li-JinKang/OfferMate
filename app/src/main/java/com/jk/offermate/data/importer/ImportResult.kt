package com.jk.offermate.data.importer

import com.jk.offermate.agent.pipeline.AnsweredQuestion
import com.jk.offermate.data.reader.PostContent

/** 一次"导入并分析"的结果。 */
sealed interface ImportResult {

    /** 读取 + 分析成功。 */
    data class Success(
        val content: PostContent,
        val questions: List<AnsweredQuestion>
    ) : ImportResult

    /** 自动读取失败，需要用户手动粘贴正文。 */
    data class NeedsManualInput(val resolvedUrl: String, val reason: String) : ImportResult

    /** 分析阶段失败（如 Key 未配置、模型调用出错）。 */
    data class Failed(val reason: String) : ImportResult
}
