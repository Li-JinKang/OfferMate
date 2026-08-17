package com.jk.offermate.data.importer

import com.jk.offermate.data.ai.AiException
import com.jk.offermate.data.ai.AnalysisPipeline
import com.jk.offermate.data.ai.ResumeProfile
import com.jk.offermate.data.ocr.OcrTextRecognizer
import com.jk.offermate.data.reader.ContentReader
import com.jk.offermate.data.reader.ExtractionMethod
import com.jk.offermate.data.reader.ImageFetcher
import com.jk.offermate.data.reader.PostContent
import com.jk.offermate.data.reader.ReadResult

/**
 * 导入编排：把"链接读取（P2）"与"AI 分析流水线（P1）"串成一个业务用例。
 *
 * 读取到正文后，若帖子含图片（面经长图），先用端侧 OCR（[ocrRecognizer]）识别图片文字并并入正文，
 * 再走分析流水线。[ocrRecognizer] / [imageFetcher] 为空时跳过 OCR（便于 JVM 单测）。
 */
class ImportInteractor(
    private val contentReader: ContentReader,
    private val analysisPipeline: AnalysisPipeline,
    private val ocrRecognizer: OcrTextRecognizer? = null,
    private val imageFetcher: ImageFetcher? = null
) : Importer {

    /** 从链接导入：读取正文 →（图片 OCR）→ 分析。读取失败则回退为"需手动粘贴"。 */
    override suspend fun importFromUrl(url: String, profile: ResumeProfile): ImportResult =
        when (val read = contentReader.read(url)) {
            is ReadResult.Success -> analyze(enrichWithImageOcr(read.content), profile)
            is ReadResult.NeedsManualInput -> ImportResult.NeedsManualInput(read.resolvedUrl, read.reason)
        }

    /** 对帖子图片逐张 OCR，把识别文字并入正文；无图片或未注入 OCR 时原样返回。 */
    private suspend fun enrichWithImageOcr(content: PostContent): PostContent {
        val recognizer = ocrRecognizer ?: return content
        val fetcher = imageFetcher ?: return content
        if (content.imageUrls.isEmpty()) return content

        val ocrTexts = content.imageUrls.mapNotNull { imageUrl ->
            val bytes = fetcher.fetch(imageUrl) ?: return@mapNotNull null
            runCatching { recognizer.recognize(bytes, imageUrl) }
                .getOrNull()?.trim()?.takeIf { it.isNotEmpty() }
        }
        if (ocrTexts.isEmpty()) return content

        val merged = buildString {
            append(content.text)
            append("\n\n【图片识别内容】\n")
            ocrTexts.forEachIndexed { i, t -> append("图${i + 1}：\n").append(t).append("\n") }
        }
        return content.copy(text = merged)
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
