package com.jk.offermate.data.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HtmlContentExtractorTest {

    private val extractor = HtmlContentExtractor()

    private fun loadFixture(name: String): String =
        requireNotNull(javaClass.getResourceAsStream("/fixtures/html/$name")) {
            "缺少测试夹具: $name"
        }.bufferedReader().use { it.readText() }

    @Test
    fun `extracts main article text from nowcoder-like page`() {
        val html = loadFixture("nowcoder_sample.html")

        val content = extractor.extract(html, "https://www.nowcoder.com/post/123")

        assertTrue("应判定为可用正文", content.isUsable)
        assertEquals(ExtractionMethod.STATIC_HTML, content.method)
        assertTrue("标题应包含字节", content.title.contains("字节"))
        // 正文应包含帖子里的面试题关键片段
        assertTrue(content.text.contains("Activity 的生命周期"))
        assertTrue(content.text.contains("Handler"))
        assertTrue(content.text.contains("LRU 缓存"))
    }

    @Test
    fun `drops navigation and footer chrome from extracted text`() {
        val html = loadFixture("nowcoder_sample.html")

        val content = extractor.extract(html, "https://www.nowcoder.com/post/123")

        // 导航/侧边栏/页脚等噪声不应进入正文
        assertFalse(content.text.contains("版权所有"))
        assertFalse(content.text.contains("相关推荐"))
    }

    @Test
    fun `marks js-rendered empty page as unusable`() {
        val html = loadFixture("xhs_jsonly.html")

        val content = extractor.extract(html, "https://www.xiaohongshu.com/explore/abc")

        // 正文由 JS 注入，静态抓取拿不到 -> 不可用，触发降级
        assertFalse("JS 渲染空壳页应判定为不可用", content.isUsable)
    }
}
