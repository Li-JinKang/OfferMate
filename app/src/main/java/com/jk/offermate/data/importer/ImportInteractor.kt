package com.jk.offermate.data.importer

import com.jk.offermate.data.ai.AiException
import com.jk.offermate.data.ai.AnalysisPipeline
import com.jk.offermate.data.ai.ResumeProfile
import com.jk.offermate.data.reader.ContentReader
import com.jk.offermate.data.reader.ExtractionMethod
import com.jk.offermate.data.reader.PostContent
import com.jk.offermate.data.reader.ReadResult

/**
 * 导入编排：把"链接读取（P2）"与"AI 分析流水线（P1）"串成一个业务用例。
 *
 * 只依赖 [ContentReader] 与 [AnalysisPipeline]，编排逻辑可在 JVM 单测中用 fake 底层组件验证。
 */
class ImportInteractor(
    private val contentReader: ContentReader,
    private val analysisPipeline: AnalysisPipeline
) : Importer {

    /** 从链接导入：读取正文 → 分析。读取失败则回退为"需手动粘贴"。 */
    override suspend fun importFromUrl(url: String, profile: ResumeProfile): ImportResult =
        when (val read = contentReader.read(url)) {
            is ReadResult.Success -> analyze(read.content, profile)
            is ReadResult.NeedsManualInput -> ImportResult.NeedsManualInput(read.resolvedUrl, read.reason)
        }

    /** 从用户手动粘贴的正文导入：直接分析。 */
    override suspend fun importFromText(text: String, profile: ResumeProfile, sourceUrl: String): ImportResult {
        val content = PostContent(
            title = "",
            text = text,
            sourceUrl = sourceUrl,
            method = ExtractionMethod.MANUAL
        )
        return analyze(content, profile)
    }

    private suspend fun analyze(content: PostContent, profile: ResumeProfile): ImportResult =
        try {
            ImportResult.Success(content, analysisPipeline.analyze(content.text, profile))
        } catch (e: AiException) {
            ImportResult.Failed(e.message ?: "分析失败")
        }
}
