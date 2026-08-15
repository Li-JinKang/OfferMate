package com.jk.offermate.data.reader

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class XhsNoteExtractorTest {

    private fun loadFixture(name: String): String =
        requireNotNull(javaClass.getResourceAsStream("/fixtures/html/$name")) {
            "缺少测试夹具: $name"
        }.bufferedReader().use { it.readText() }

    @Test
    fun `extracts note title and desc without comments and recommendations`() {
        val html = loadFixture("xhs_note.html")

        val content = XhsNoteExtractor.extract(html, "https://www.xiaohongshu.com/discovery/item/abc")

        assertNotNull(content)
        content!!
        assertTrue(content.isUsable)
        assertTrue(content.title.contains("TME客户端面经"))
        // 含正文
        assertTrue(content.text.contains("腾讯音乐的客户端面经"))
        // 话题占位已清理
        assertFalse(content.text.contains("[话题]"))
        // 不含评论与推荐噪声
        assertFalse(content.text.contains("请问没有手撕代码"))
        assertFalse(content.text.contains("加班多不多"))
        assertFalse(content.text.contains("猜你喜欢"))
    }

    @Test
    fun `returns null when no initial state`() {
        val html = "<html><body><div id=app></div></body></html>"
        assertNull(XhsNoteExtractor.extract(html, "https://www.xiaohongshu.com/x"))
    }
}
