package com.jk.offermate.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [InlineMarkdown] 的两条契约。
 *
 * **一、判定必须只放过它真的渲染得对的形态。** 放过一个结构更复杂的块，就会丢掉它的结构
 * （代码块没了卡片、嵌套列表被压平、任务列表少了复选框），而且是静默丢——没有异常、没有日志。
 *
 * **二、产出的可见文本要与完整渲染器一致。** 两条路径渲染同一段文字，差一个空格都会在
 * 「流式尾块 → 定稿块」切换那一刻看出位移。这里锁住几个容易出错的点（行内代码两侧的空格、
 * 段落内换行折叠、行内标记不残留、列表标记的生成规则），渲染库升级改了行为的话会在这里先炸。
 */
class InlineMarkdownTest {

    private fun block(markdown: String) = InlineMarkdown.annotate(markdown, store = false)

    private fun plainText(markdown: String): String? = block(markdown)?.text?.text

    @Test
    fun `段落走轻量路径`() {
        assertNotNull(plainText("这是一段普通的中文正文。"))
        assertNotNull(plainText("带 **加粗** 与 *斜体* 以及 ~~删除线~~ 的段落。"))
        assertNotNull(plainText("含 `inlineCode` 的段落。"))
        // 尾随换行（切块产物的常见形态）仍是单块
        assertNotNull(plainText("段落正文\n"))
        assertNotNull(plainText("段落正文\n\n"))
    }

    @Test
    fun `标题走轻量路径且带标题语义`() {
        (1..6).forEach { level ->
            val result = block("${"#".repeat(level)} 标题 $level\n")
            assertNotNull("$level 级标题该走轻量路径", result)
            assertTrue("标题要带 heading 语义", result!!.isHeading)
            assertEquals("标题 $level", result.text.text)
        }
        // 字号必须与完整渲染器用的同一份常量，否则定稿切换时标题会跳字号
        assertEquals(H1Style, block("# 大标题\n")!!.style)
        assertEquals(H3Style, block("### 小标题\n")!!.style)
    }

    @Test
    fun `单个列表项走轻量路径`() {
        val bullet = block("- 列表项内容\n")
        assertNotNull(bullet)
        assertEquals("• ", bullet!!.marker)
        assertEquals("列表项内容", bullet.text.text)
        assertTrue("无序 bullet 要带额外的底部留白（复刻库的不对称）", bullet.markerBottomPadding)

        listOf("*", "+").forEach {
            assertEquals("• ", block("$it 列表项\n")!!.marker)
        }
    }

    /**
     * 有序项的序号取自 CommonMark 的 start number（首个列表项的数字），
     * 所以 `3. xxx` 被单独切成一块后仍显示为 3——这是 [StreamingMarkdown] 按列表项下刀的前提。
     */
    @Test
    fun `有序项保留原始序号`() {
        assertEquals("1. ", block("1. 第一项\n")!!.marker)
        assertEquals("3. ", block("3. 第三项\n")!!.marker)
        assertEquals("12. ", block("12. 第十二项\n")!!.marker)
        assertEquals("有序项不该带额外底部留白", false, block("1. 第一项\n")!!.markerBottomPadding)
        assertEquals(")", ")") // 保持 `3)` 形式也被解析为有序列表
        assertNotNull(block("2) 第二项\n"))
    }

    @Test
    fun `结构更复杂的块一律回退`() {
        val complex = listOf(
            "```kotlin\nfun main() {}\n```\n",
            "```kotlin\nfun main() {\n",
            "> 引用行\n",
            "| 列1 | 列2 |\n|---|---|\n| a | b |\n",
            "---\n",
            "    缩进代码块\n",
            // 多段落（合并块的形态）
            "第一段\n\n第二段\n",
            // 一个块里多个列表项：结构没问题，但两项之间的间距要靠库的 Column，不复刻
            "- 第一项\n- 第二项\n",
            // 嵌套子列表
            "- 父项\n  - 子项\n",
            // 任务列表要复选框 composable
            "- [ ] 待办\n",
            // 空内容
            "",
            "\n"
        )
        complex.forEach { assertNull("不该走轻量路径：${it.take(20)}", plainText(it)) }
    }

    @Test
    fun `图片与链接回退`() {
        // 图片要 Text 侧提供 inlineContent，链接要 uriHandler 与引用定义表，轻量路径都没有。
        assertNull(plainText("看图 ![alt](https://example.com/a.png) 结束"))
        assertNull(plainText("点这里 [文档](https://example.com) 看看"))
        assertNull(plainText("裸链接 https://example.com 也算"))
        assertNull(plainText("自动链接 <https://example.com> 也算"))
        assertNull(plainText("引用式 [文档][doc] 链接"))
        // 标题与列表项里的链接同样要回退
        assertNull(plainText("## 见 [文档](https://example.com)\n"))
        assertNull(plainText("- 见 [文档](https://example.com)\n"))
    }

    @Test
    fun `行内标记不残留在可见文本里`() {
        assertEquals("加粗文字", plainText("**加粗文字**"))
        assertEquals("斜体文字", plainText("*斜体文字*"))
        assertEquals("删除文字", plainText("~~删除文字~~"))
        assertEquals("带加粗的标题", plainText("## 带**加粗**的标题\n"))
        assertEquals("带加粗的项", plainText("- 带**加粗**的项\n"))
    }

    /**
     * 渲染库给行内代码两侧各补一个空格（`AnnotatedStringKtx` 的 CODE_SPAN 分支），
     * 轻量路径因为直接调库函数所以自动一致。这条断言就是为了在库改掉这个行为时立刻发现——
     * 否则定稿切换时整行文字会横向位移。
     */
    @Test
    fun `行内代码两侧各补一个空格`() {
        // 源文本里 backtick 两侧本来就有空格，库再各补一个，于是可见文本是两个空格。
        assertEquals("调用  a()  完成", plainText("调用 `a()` 完成"))
        // 紧贴中文时只有库补的那一个
        assertEquals("调用 a() 完成", plainText("调用`a()`完成"))
    }

    /** 段落内部的换行在 Markdown 里不是换行，会折叠成空格。 */
    @Test
    fun `段落内换行折叠成空格`() {
        assertEquals("第一行 第二行", plainText("第一行\n第二行"))
    }

    /**
     * 块级留白要跟着尾随换行数走：库在每个顶层节点前插一份 `padding.block`，连不产出内容的
     * 换行也算。算错的话同一块从流式（末尾还没换行）切到定稿（多出换行）时间距会跳。
     */
    @Test
    fun `尾随换行数决定块级留白`() {
        assertEquals(0, block("段落")!!.trailingBreaks)
        assertEquals(1, block("段落\n")!!.trailingBreaks)
        assertEquals(2, block("段落\n\n")!!.trailingBreaks)
        assertEquals(2, block("- 列表项\n\n")!!.trailingBreaks)
    }

    @Test
    fun `渐显只覆盖末尾若干字符且不改文本`() {
        val base = block("一二三四五六七八九十甲乙丙丁")!!
        val faded = base.faded(count = 4)

        assertEquals("渐显不该改动文本", base.text.text, faded.text.text)
        // 末尾 4 个字符各有一个 alpha span；原样式区间数不变。
        assertEquals(4, faded.text.spanStyles.size - base.text.spanStyles.size)
        val tail = faded.text.spanStyles.takeLast(4)
        assertEquals(base.text.length - 4, tail.first().start)
        assertEquals(base.text.length, tail.last().end)
        // 越靠末尾越淡
        val alphas = tail.map { it.item.color.alpha }
        assertTrue("alpha 应当单调递减：$alphas", alphas.zipWithNext().all { (a, b) -> a > b })
    }

    @Test
    fun `渐显保留块的渲染参数`() {
        val listItem = block("- 列表项内容\n")!!.faded()
        assertEquals("• ", listItem.marker)
        assertEquals(1, listItem.trailingBreaks)

        val heading = block("## 标题\n")!!.faded()
        assertTrue(heading.isHeading)
        assertEquals(H2Style, heading.style)
    }

    @Test
    fun `渐显对短文本与空文本安全`() {
        val base = block("短")!!
        assertEquals("短", base.faded(count = 10).text.text)
        assertEquals(base.text.text, base.faded(count = 0).text.text)
    }

    /** 逐帧解析递增前缀不能崩、不能出现「有结果但文本为空」这种中间态。 */
    @Test
    fun `递增前缀逐帧解析结果稳定`() {
        val full = "一段会逐字生成的正文，中间有 **加粗** 和 `code`，最后收尾。"
        for (i in 1..full.length) {
            val result = block(full.substring(0, i))
            if (result != null) assertTrue(result.text.text.isNotEmpty())
        }
        assertEquals("一段会逐字生成的正文，中间有 加粗 和  code ，最后收尾。", plainText(full))
    }
}
