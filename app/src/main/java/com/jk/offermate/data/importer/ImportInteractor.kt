package com.jk.offermate.data.importer

import com.jk.offermate.agent.AiException
import com.jk.offermate.agent.pipeline.AnsweredQuestion
import com.jk.offermate.agent.pipeline.PostAnalyzer
import com.jk.offermate.agent.pipeline.QuestionCategorizer
import com.jk.offermate.data.ocr.OcrTextRecognizer
import com.jk.offermate.data.reader.ContentReader
import com.jk.offermate.data.reader.ExtractionMethod
import com.jk.offermate.data.reader.ImageFetcher
import com.jk.offermate.data.reader.PostContent
import com.jk.offermate.data.reader.ReadResult
import com.jk.offermate.data.repository.CategoryRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first

/**
 * 导入编排：把"链接读取（P2）"与"AI 分析流水线（P1）"串成一个业务用例。
 *
 * 读取到正文后，若帖子含图片（面经长图），先用端侧 OCR（[ocrRecognizer]）识别图片文字并并入正文，
 * 再走分析流水线；分析出题目后由 [categoryClassifier] 结合已有分类归类（可新建，写回 [categoryRepository]）。
 * 可选依赖为空时相应步骤跳过（便于 JVM 单测）。
 */
class ImportInteractor(
    private val contentReader: ContentReader,
    private val analyzer: PostAnalyzer,
    private val ocrRecognizer: OcrTextRecognizer? = null,
    private val imageFetcher: ImageFetcher? = null,
    private val categorizer: QuestionCategorizer? = null,
    private val categoryRepository: CategoryRepository? = null
) : Importer {

    /** 从链接导入：读取正文 →（图片 OCR）→ 分析。读取失败则回退为"需手动粘贴"。 */
    override suspend fun importFromUrl(url: String): ImportResult =
        when (val read = contentReader.read(url)) {
            is ReadResult.Success -> analyze(enrichWithImageOcr(read.content))
            is ReadResult.NeedsManualInput -> ImportResult.NeedsManualInput(read.resolvedUrl, read.reason)
        }

    /** 对帖子图片逐张 OCR，把识别文字并入正文；无图片或未注入 OCR 时原样返回。 */
    private suspend fun enrichWithImageOcr(content: PostContent): PostContent {
        val recognizer = ocrRecognizer ?: return content
        val fetcher = imageFetcher ?: return content
        if (content.imageUrls.isEmpty()) return content

        val ocrTexts = content.imageUrls.mapNotNull { imageUrl ->
            val bytes = fetcher.fetch(imageUrl) ?: return@mapNotNull null
            tolerate { recognizer.recognize(bytes, imageUrl) }?.trim()?.takeIf { it.isNotEmpty() }
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
    override suspend fun importFromText(text: String, sourceUrl: String): ImportResult {
        val content = PostContent(
            title = "",
            text = text,
            sourceUrl = sourceUrl,
            method = ExtractionMethod.MANUAL
        )
        return analyze(content)
    }

    private suspend fun analyze(content: PostContent): ImportResult =
        try {
            val questions = analyzer.analyze(content.text)
            ImportResult.Success(content, categorize(questions))
        } catch (e: AiException) {
            // retryable 原样透传给任务层，由它决定重试还是落终态失败
            ImportResult.Failed(e.message ?: "分析失败", retryable = e.retryable)
        }

    /**
     * 让 LLM 结合"已有分类"为题目归类；新分类写回本地供下次复用。
     * 未注入分类器时原样返回（UI 会用启发式归并兜底）。分类失败不影响导入结果。
     */
    private suspend fun categorize(questions: List<AnsweredQuestion>): List<AnsweredQuestion> {
        val strategy = categorizer ?: return questions
        if (questions.isEmpty()) return questions

        val existing = categoryRepository
            ?.let { repo -> tolerate { repo.observeCategories().first() } }
            ?: emptyList()

        val categorized = tolerate { strategy.categorize(questions, existing) } ?: questions

        categoryRepository?.let { repo ->
            categorized.map { it.category }
                .filter { it.isNotBlank() && it !in existing }
                .distinct()
                .forEach { tolerate { repo.addCategory(it) } }
        }
        return categorized
    }

    /**
     * 执行 [block]，失败返回 null——但**放行 [CancellationException]**。
     *
     * 不用 `runCatching`：它会把协程取消也当成普通失败吞掉，导致任务被取消后流程继续往下跑，
     * 最终在已取消的 scope 上写库（见 docs/plan/network-resilience.md 第 4 节）。
     */
    private suspend fun <T> tolerate(block: suspend () -> T): T? =
        try {
            block()
        } catch (c: CancellationException) {
            throw c
        } catch (e: Exception) {
            null
        }
}
