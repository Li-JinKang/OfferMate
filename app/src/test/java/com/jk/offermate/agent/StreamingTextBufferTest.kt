package com.jk.offermate.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [StreamingTextBuffer] 的三条契约。
 *
 * 1. 普通正文必须**完整且按序**透出（不丢字、不串字）；
 * 2. 标签形态的工具标记要静默，但**裸词 `invoke` / `tool_calls` 不能触发静默**——
 *    这是面经 App 的高频正文（`Method.invoke`、function calling 的 `tool_calls` 字段）；
 * 3. 增量扫描不能因 chunk 边界切开标记而漏判。
 */
class StreamingTextBufferTest {

    private class Sink {
        val chunks = mutableListOf<String>()
        val text: String get() = chunks.joinToString("")
    }

    private fun feed(vararg deltas: String): Pair<Sink, StreamingTextBuffer> {
        val sink = Sink()
        val buffer = StreamingTextBuffer { sink.chunks.add(it) }
        deltas.forEach { buffer.append(it) }
        return sink to buffer
    }

    @Test
    fun `普通正文逐块透出且不丢字`() {
        val (sink, buffer) = feed("你好", "，这是", "一段回答。")
        buffer.flushRemaining()
        assertEquals("你好，这是一段回答。", sink.text)
        assertFalse(buffer.isSilenced())
    }

    @Test
    fun `未闭合的尖括号暂缓到闭合后才透出`() {
        val (sink, buffer) = feed("泛型 List<")
        // `<` 之后还没有 `>`，这一小段先压住
        assertEquals("泛型 List", sink.text)

        buffer.append("String> 用法")
        buffer.flushRemaining()
        assertEquals("泛型 List<String> 用法", sink.text)
    }

    @Test
    fun `流结束时未闭合的尖括号也要补齐`() {
        val (sink, buffer) = feed("比较 a <")
        assertEquals("比较 a ", sink.text)
        buffer.flushRemaining()
        assertEquals("比较 a <", sink.text)
    }

    /** 回归：正文里的裸词曾让整段回答完全不流式。 */
    @Test
    fun `正文里的裸词不触发静默`() {
        val plainWords = listOf(
            "反射的核心是 Method.invoke，它会…",
            "OpenAI 的 tool_calls 字段用来传结构化工具调用。",
            "invoke 这个词单独出现也不算标记",
            "代码里写 method.invoke(obj) 即可"
        )
        plainWords.forEach { text ->
            val (sink, buffer) = feed(text)
            buffer.flushRemaining()
            assertFalse("不该静默：$text", buffer.isSilenced())
            assertEquals(text, sink.text)
        }
    }

    @Test
    fun `标签形态的工具标记触发静默`() {
        val markups = listOf(
            "<invoke name=\"load_memory\">",
            "<tool_calls><invoke name=\"x\">",
            "<invoke name=\"x\">",
            "</invoke>"
        )
        markups.forEach { text ->
            val (sink, buffer) = feed(text)
            buffer.flushRemaining()
            assertTrue("该静默：$text", buffer.isSilenced())
            assertEquals("静默后不该有任何回调：$text", "", sink.text)
        }
    }

    /** 标记被 chunk 边界切成两半时仍要判出来——这是增量扫描最容易漏的地方。 */
    @Test
    fun `标记跨chunk边界仍能判出`() {
        val (sink, buffer) = feed("<inv", "oke name=\"x\">")
        buffer.flushRemaining()
        assertTrue(buffer.isSilenced())
        assertEquals("", sink.text)
    }

    /** 逐字符喂入（最坏的 chunk 粒度）也要判出来。 */
    @Test
    fun `逐字符喂入也能判出标记`() {
        val sink = Sink()
        val buffer = StreamingTextBuffer { sink.chunks.add(it) }
        "正文开头<tool_calls>".forEach { buffer.append(it.toString()) }
        buffer.flushRemaining()
        assertTrue(buffer.isSilenced())
        // 静默前已透出的正文不回收，但标记本身一个字都不该出去
        assertFalse(sink.text.contains("tool_calls"))
    }

    @Test
    fun `finish返回完整原文`() {
        val (_, buffer) = feed("abc", "<invoke name=\"x\">", "def")
        assertEquals("abc<invoke name=\"x\">def", buffer.finish())
    }

    /** 增量扫描不能因为已扫过的前缀而改变透出结果。 */
    @Test
    fun `长文本分块喂入与整段喂入结果一致`() {
        val full = buildString {
            repeat(40) { append("第 $it 段正文，含 a < b 的比较与 List<Int> 泛型。") }
        }
        val whole = feed(full).also { it.second.flushRemaining() }.first.text
        val sink = Sink()
        val buffer = StreamingTextBuffer { sink.chunks.add(it) }
        full.chunked(3).forEach { buffer.append(it) }
        buffer.flushRemaining()
        assertEquals(whole, sink.text)
        assertEquals(full, sink.text)
    }
}
