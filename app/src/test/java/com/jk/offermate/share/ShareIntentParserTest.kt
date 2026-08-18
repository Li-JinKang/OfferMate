package com.jk.offermate.share

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ShareIntentParserTest {

    @Test
    fun `extracts pure link`() {
        val text = "https://www.nowcoder.com/share/jump/53245626677297352"
        assertEquals(text, ShareIntentParser.extractLink(text))
    }

    @Test
    fun `extracts link embedded in title text`() {
        val text = "这道面试题真难 https://xhslink.cn/o/6Gz0nDGZxAE 大家来看看"
        assertEquals("https://xhslink.cn/o/6Gz0nDGZxAE", ShareIntentParser.extractLink(text))
    }

    @Test
    fun `strips trailing punctuation from link`() {
        val text = "分享链接：https://xhslink.cn/o/abc。"
        assertEquals("https://xhslink.cn/o/abc", ShareIntentParser.extractLink(text))
    }

    @Test
    fun `returns null when no link present`() {
        assertNull(ShareIntentParser.extractLink("这是一段没有链接的纯文本面经内容"))
    }

    @Test
    fun `returns null for blank or null input`() {
        assertNull(ShareIntentParser.extractLink(null))
        assertNull(ShareIntentParser.extractLink("   "))
    }
}
