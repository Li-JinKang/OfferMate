package com.jk.offermate.data.importer

import com.jk.offermate.data.ai.AiException
import com.jk.offermate.data.ai.AnalysisPipeline
import com.jk.offermate.data.ai.AnswerGenerator
import com.jk.offermate.data.ai.ChatMessage
import com.jk.offermate.data.ai.FakeAiClient
import com.jk.offermate.data.ai.QuestionExtractor
import com.jk.offermate.data.ai.RelevanceMatcher
import com.jk.offermate.data.ai.ResumeProfile
import com.jk.offermate.data.reader.ContentReader
import com.jk.offermate.data.reader.DynamicContentReader
import com.jk.offermate.data.reader.HtmlContentExtractor
import com.jk.offermate.data.reader.HtmlFetcher
import com.jk.offermate.data.reader.PostContent
import com.jk.offermate.data.reader.UrlResolver
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ImportInteractorTest {

    private fun loadLlm(name: String): String =
        requireNotNull(javaClass.getResourceAsStream("/fixtures/llm/$name")).bufferedReader().use { it.readText() }

    private fun loadHtml(name: String): String =
        requireNotNull(javaClass.getResourceAsStream("/fixtures/html/$name")).bufferedReader().use { it.readText() }

    private val profile = ResumeProfile(targetRole = "Android 开发", skills = listOf("Android"))

    private class FakeResolver(private val out: String) : UrlResolver {
        override fun resolve(url: String): String = out
    }

    private class FakeFetcher(private val html: String?) : HtmlFetcher {
        override fun fetch(url: String): String? = html
    }

    private class FakeDynamic(private val content: PostContent?) : DynamicContentReader {
        override suspend fun read(url: String): PostContent? = content
    }

    private fun realPipeline() = AnalysisPipeline(
        extractor = QuestionExtractor(FakeAiClient.returning(loadLlm("extract_response.json"))),
        matcher = RelevanceMatcher(FakeAiClient.returning(loadLlm("relevance_response.json"))),
        answerer = AnswerGenerator(FakeAiClient.returning(loadLlm("answer_response.json")))
    )

    private fun reader(html: String?, dynamic: PostContent? = null) = ContentReader(
        urlResolver = FakeResolver("https://www.nowcoder.com/post/1"),
        htmlFetcher = FakeFetcher(html),
        extractor = HtmlContentExtractor(),
        dynamicReader = FakeDynamic(dynamic)
    )

    @Test
    fun `importFromUrl reads and analyzes into answered questions`() = runTest {
        val interactor = ImportInteractor(reader(loadHtml("nowcoder_sample.html")), realPipeline())

        val result = interactor.importFromUrl("https://www.nowcoder.com/share/jump/x", profile)

        assertTrue(result is ImportResult.Success)
        val success = result as ImportResult.Success
        assertEquals(2, success.questions.size)
        assertTrue(success.questions[0].question.contains("Activity"))
        assertEquals(95, success.questions[0].relevanceScore)
    }

    @Test
    fun `importFromUrl runs OCR on images and merges recognized text into content`() = runTest {
        val html = "<html><body><p>${"面经正文内容。".repeat(10)}</p>" +
            "<img src=\"https://cdn.example.com/pic.jpg\"/></body></html>"
        val fakeOcr = object : com.jk.offermate.data.ocr.OcrTextRecognizer {
            override suspend fun recognize(imageBytes: ByteArray, source: String) = "图中的题目：什么是协程"
        }
        val fakeFetcher = object : com.jk.offermate.data.reader.ImageFetcher {
            override suspend fun fetch(url: String) = ByteArray(8)
        }
        val interactor = ImportInteractor(reader(html), realPipeline(), fakeOcr, fakeFetcher)

        val result = interactor.importFromUrl("https://www.nowcoder.com/share/jump/x", profile)

        assertTrue(result is ImportResult.Success)
        val content = (result as ImportResult.Success).content
        assertTrue(content.imageUrls.isNotEmpty())
        assertTrue(content.text.contains("【图片识别内容】"))
        assertTrue(content.text.contains("图中的题目：什么是协程"))
    }

    @Test
    fun `importFromUrl returns NeedsManualInput when read fails`() = runTest {
        val interactor = ImportInteractor(reader(html = null, dynamic = null), realPipeline())

        val result = interactor.importFromUrl("https://xhslink.cn/o/x", profile)

        assertTrue(result is ImportResult.NeedsManualInput)
    }

    @Test
    fun `importFromText analyzes pasted content`() = runTest {
        val interactor = ImportInteractor(reader(html = null), realPipeline())

        val result = interactor.importFromText("一段用户粘贴的面经正文", profile)

        assertTrue(result is ImportResult.Success)
        assertEquals(2, (result as ImportResult.Success).questions.size)
    }

    @Test
    fun `returns Failed when analysis raises AiException`() = runTest {
        val throwingPipeline = AnalysisPipeline(
            extractor = QuestionExtractor(FakeAiClient { _: List<ChatMessage> -> throw AiException("未配置 Key") }),
            matcher = RelevanceMatcher(FakeAiClient.returning("")),
            answerer = AnswerGenerator(FakeAiClient.returning(""))
        )
        val interactor = ImportInteractor(reader(html = null), throwingPipeline)

        val result = interactor.importFromText("正文", profile)

        assertTrue(result is ImportResult.Failed)
    }
}
