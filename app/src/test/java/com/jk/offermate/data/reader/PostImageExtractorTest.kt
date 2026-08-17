package com.jk.offermate.data.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PostImageExtractorTest {

    private fun loadFixture(name: String): String =
        requireNotNull(javaClass.getResourceAsStream("/fixtures/html/$name")) {
            "缺少测试夹具: $name"
        }.bufferedReader().use { it.readText() }

    @Test
    fun `extracts current note images as detail urls in order`() {
        val urls = PostImageExtractor.extractXhsImageUrls(loadFixture("xhs_imagelist_sample.html"))

        assertEquals(
            listOf(
                "http://sns-webpic-qc.xhscdn.com/202608/aaa/notes_pre_post/IMG1!h5_1080jpg",
                "http://sns-webpic-qc.xhscdn.com/202608/ccc/notes_pre_post/IMG2!h5_1080jpg"
            ),
            urls
        )
    }

    @Test
    fun `escaped slashes are decoded`() {
        val urls = PostImageExtractor.extractXhsImageUrls(loadFixture("xhs_imagelist_sample.html"))
        assertTrue(urls.all { !it.contains("\\u002F") && it.startsWith("http://") })
    }

    @Test
    fun `does not pick preview avatar or recommended note images`() {
        val urls = PostImageExtractor.extractXhsImageUrls(loadFixture("xhs_imagelist_sample.html"))
        assertFalse(urls.any { it.contains("style_prv") }) // 预览小图
        assertFalse(urls.any { it.contains("avatar") })    // 评论头像
        assertFalse(urls.any { it.contains("/REC!") })     // 推荐笔记
    }

    @Test
    fun `returns empty when no initial state`() {
        assertEquals(emptyList<String>(), PostImageExtractor.extractXhsImageUrls("<html><body>no state</body></html>"))
    }

    @Test
    fun `generic html img extraction absolutizes and dedupes`() {
        val html = """
            <html><body>
              <img src="/img/a.png"/>
              <img src="https://cdn.example.com/b.jpg"/>
              <img src="/img/a.png"/>
              <img src="data:image/png;base64,AAAA"/>
            </body></html>
        """.trimIndent()

        val urls = PostImageExtractor.extractHtmlImageUrls(html, "https://www.nowcoder.com/post/1")

        assertEquals(
            listOf(
                "https://www.nowcoder.com/img/a.png",
                "https://cdn.example.com/b.jpg"
            ),
            urls
        )
    }
}
