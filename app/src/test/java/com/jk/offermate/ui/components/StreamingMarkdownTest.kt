package com.jk.offermate.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [StreamingMarkdown.blocks] 的切分契约。
 *
 * 重点守两条：切分**绝不改动内容**（否则流式期间会丢字或串字），
 * 以及最后一块保持独立（它是流式正在生成的那块，必须最小）。
 */
class StreamingMarkdownTest {

    /** 构造一篇带标题/段落/列表/代码块的长回答，接近真实 AI 输出。 */
    private fun longAnswer(rounds: Int): String = buildString {
        for (i in 1..rounds) {
            append("## 小标题 $i\n\n")
            append("这是一段中文正文，用来模拟模型输出的散文段落，里面有 **加粗** 和 `inlineCode`。\n\n")
            append("- 列表项 A$i\n- 列表项 B$i\n- 列表项 C$i\n\n")
            if (i % 3 == 0) append("```kotlin\nfun demo$i() {\n    println(\"hi\")\n}\n```\n\n")
        }
    }

    @Test
    fun `切分后拼回原文`() {
        val samples = listOf(
            "",
            "短文本不切",
            "# 标题\n\n正文一段\n\n正文两段\n",
            longAnswer(1),
            longAnswer(8),
            // 未闭合围栏：整段都在代码块里，不该切
            "正文\n\n```kotlin\nfun a() {\n",
            // 表格
            "说明\n\n| 列1 | 列2 |\n|---|---|\n| a | b |\n| c | d |\n\n收尾段落\n"
        )
        samples.forEach { text ->
            val blocks = StreamingMarkdown.blocks(text)
            assertEquals("切分改动了内容：${text.take(40)}", text, blocks.joinToString(""))
            assertTrue("至少要有一块", blocks.isNotEmpty())
        }
    }

    @Test
    fun `未闭合围栏不切分`() {
        val text = "前面一段正文，足够长来越过最小切分长度限制，再补一点字数让它确实超过两百个字符。" +
            "继续补充一些内容，确保触发切分逻辑而不是走短文本直接返回的那条分支。\n\n```kotlin\nfun a() {\n"
        assertEquals(listOf(text), StreamingMarkdown.blocks(text))
    }

    @Test
    fun `合并短块后块数明显下降且最后一块独立`() {
        val text = longAnswer(8)
        val blocks = StreamingMarkdown.blocks(text)

        // 合并前按列表项下刀会切出上百块；合并后应显著减少。
        assertTrue("块数没降下来：${blocks.size}", blocks.size < 40)

        // 除最后一块外，每块都应达到下限（或是攒不够长度的那个收尾组）。
        val settled = blocks.dropLast(1)
        assertTrue("固化块不该有大量超短块", settled.count { it.length < 80 } <= 1)
    }

    /**
     * 已定稿的块必须逐字稳定，否则 [MarkdownStateCache] 全程命中不上、每帧都要重解析。
     *
     * 末尾留 2 个元素的 slack，这两个本来就还在变：
     * 1. 正在生成的那个原始块；
     * 2. 还没攒够 `MIN_BLOCK_LENGTH`、仍在吸收后续块的那个合并组。
     *
     * 必须**经过 [PartialMarkdown.sanitize]** 再切——这就是线上的真实链路。直接切裸文本时，
     * 未闭合的代码围栏会让 `blocks` 整篇返回一块，边界自然对不上。
     */
    @Test
    fun `已定稿的块不随后续追加而改变`() {
        val full = longAnswer(6)
        var previous: List<String> = emptyList()
        for (step in 50..full.length step 7) {
            val current = StreamingMarkdown.blocks(PartialMarkdown.sanitize(full.substring(0, step)))
            if (previous.isNotEmpty()) {
                val stable = minOf(previous.size - 2, current.size - 2)
                for (i in 0 until stable) {
                    assertEquals("第 $i 块在追加内容后被改写了（step=$step）", previous[i], current[i])
                }
            }
            previous = current
        }
    }
}
