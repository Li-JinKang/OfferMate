package com.jk.offermate.ui.components

/**
 * 流式 Markdown 的**结构感知修正**：把"写了一半"的 Markdown 补成结构稳定的形态再交给渲染器。
 *
 * 为什么需要 —— 渲染半截 Markdown 时，未闭合的标记会被解析成完全不同的块级结构：
 * ```
 * ```kotlin
 * fun main
 * ```
 * 缺少闭合围栏时这段是**普通段落**，等下一个 token 补上 ``` 的瞬间整块变成**代码卡片**，
 * 高度和样式骤变，表现为剧烈闪烁跳跃。
 *
 * 只作用于流式中间态；定稿文本原样渲染，绝不把补出来的字符写回数据库。
 */
object PartialMarkdown {

    /** 行首的 ``` 围栏（允许前置空格/制表符）。 */
    private val FENCE = Regex("^[ \t]*```", RegexOption.MULTILINE)

    /** 需要平衡的行内标记。单个 `*` / `_` 与列表项、强调混用歧义太大，不处理。 */
    private val INLINE_MARKERS = listOf("**", "~~", "`")

    /**
     * @param text 已经过打字机截断的流式文本
     * @return 结构补全后的文本，长度可能与入参不同
     */
    fun sanitize(text: String): String {
        if (text.isEmpty()) return text

        // 1) 未闭合的代码围栏：补一个闭合围栏。半截代码块从一开始就以代码卡片渲染，
        //    后续内容在卡片内增长，不再有"段落 → 代码块"的结构跳变。
        if (FENCE.findAll(text).count() % 2 != 0) {
            val quantized = quantizeInsideFence(text)
            return if (quantized.endsWith("\n")) quantized + "```" else quantized + "\n```"
        }

        // 2) 行内标记只在**最后一个块**内平衡：Markdown 的行内解析以块为单位，
        //    改动更早的块既没必要也有风险。
        val cut = text.lastIndexOf("\n\n")
        val head = if (cut < 0) "" else text.substring(0, cut + 2)
        var tail = if (cut < 0) text else text.substring(cut + 2)

        // 尾块里还有 ``` 说明它落在代码块内部（空行出现在围栏之间），
        // 此时任何行内标记的增删都可能破坏围栏，直接跳过。
        if (!tail.contains("```")) {
            // 表格与行内标记平衡互斥：balance 会往尾部追加标记，落在分隔行上就会毁掉表格结构。
            // 表格单元格内容短，中途悬着未闭合标记的概率也低，这里让表格结构优先。
            tail = if (tail.lineSequence().any { it.trimStart().startsWith("|") }) {
                completeTableStructure(tail)
            } else {
                INLINE_MARKERS.fold(tail) { acc, marker -> balance(acc, marker) }
            }
        }
        return head + tail
    }

    /**
     * 平衡某个行内标记：出现奇数次说明有一个没闭合。
     * - 悬在结尾（如 `...**`）→ 去掉它，避免裸标记字符先以字面量闪一下。
     * - 在中间（如 `**加粗中`）→ 在结尾补上闭合，让它立刻以粗体呈现，后续文字继续在粗体内增长。
     */
    private fun balance(text: String, marker: String): String {
        var count = 0
        var i = 0
        while (true) {
            val hit = text.indexOf(marker, i)
            if (hit < 0) break
            count++
            i = hit + marker.length
        }
        if (count % 2 == 0) return text
        return if (text.endsWith(marker)) text.dropLast(marker.length) else text + marker
    }

    /**
     * 代码块内的内容**按行对齐**：正在写的那一行先不揭示。
     *
     * 代码卡片每次内容变化都要整块重新做等宽文本布局（还挂着 horizontalScroll），逐字符更新
     * 等于每个出字节拍重排整块代码，是掉帧的主要来源之一。改成一行写完才揭示后，
     * 一个几十行的代码块从「几百次更新」降到「几十次更新」，观感上就是逐行出现——和主流 AI 对话一致。
     *
     * 只对**已经打开的围栏内部**生效：正在写的开围栏行（``` 或 ```kotlin）本身不能摘，
     * 否则会把刚成形的代码块结构又拆回段落。
     */
    private fun quantizeInsideFence(text: String): String {
        val lastBreak = text.lastIndexOf('\n')
        if (lastBreak < 0) return text
        val head = text.substring(0, lastBreak + 1)
        // head 里围栏仍是奇数个，说明被摘掉的那一行确实在代码块**内部**。
        return if (FENCE.findAll(head).count() % 2 != 0) head else text
    }

    /** 分隔行允许出现的字符。 */
    private val DELIMITER_CHARS = setOf('|', '-', ':', ' ')

    /**
     * 让表格结构**尽早成形**，这样后续行能真正在表格内逐行流式出现。
     *
     * GFM 只有在「表头 + 分隔行」都齐了才认表格。分隔行往往和表头一样长，在它写完之前
     * 这两行都以普通段落渲染，之前的做法是把没写完的分隔行摘掉、等它齐了再整体变表格——
     * 于是表格总要等分隔行落地才出现，中间还有一次段落到表格的结构跳变。
     *
     * 这里改为：表头一行写完（以 `|` 收尾）就**补一条列数匹配的分隔行**，表格立刻以表头形态渲染，
     * 模型真正写出的分隔行到达前一直用补出来的顶着。结构一旦成形（出现完整分隔行）就原样放行，
     * 正在写的那一行本身就是合法表格行，会自然地在表格内增长。
     */
    private fun completeTableStructure(text: String): String {
        val lines = text.split("\n")
        // 末尾连续的表格行区块。
        var start = lines.size
        while (start > 0 && lines[start - 1].trimStart().startsWith("|")) start--
        if (start == lines.size) return text

        val block = lines.subList(start, lines.size)
        // 已有完整分隔行：表格结构成形，此后**按行对齐**地揭示。
        // MarkdownTable 每次内容变化都要重新测量所有列宽、重建全部单元格 composable，
        // 逐字符更新等于每个出字节拍重排整张表，表格越长越贵——这是掉帧最严重的一处。
        // 一行写完才揭示后，一张 10 行的表只更新 10 次，视觉上就是逐行填充。
        if (block.any { isDelimiterRow(it) }) {
            return if (isCompleteRow(lines.last().trim())) text else lines.dropLast(1).joinToString("\n")
        }

        val header = block.first().trim()
        // 表头还没写完就先当普通段落，避免列数每来一个字符就变一次、表格宽度反复重算。
        if (!isCompleteRow(header)) return text
        // 表头之后最多允许一行（正在写的分隔行）；再多说明结构不符合预期，不猜。
        if (block.size > 2) return text

        return (lines.subList(0, start) + header + delimiterFor(header)).joinToString("\n")
    }

    /** 完整的一行表格行：`| ... |`。 */
    private fun isCompleteRow(line: String): Boolean =
        line.length >= 2 && line.startsWith("|") && line.endsWith("|")

    private fun isDelimiterRow(line: String): Boolean {
        val trimmed = line.trim()
        return isCompleteRow(trimmed) && trimmed.contains('-') && trimmed.all { it in DELIMITER_CHARS }
    }

    /** 按表头列数生成分隔行。列数不匹配的话 GFM 不认表格，所以必须以表头为准。 */
    private fun delimiterFor(header: String): String =
        (1..columnCount(header)).joinToString(separator = "|", prefix = "|", postfix = "|") { "---" }

    private fun columnCount(row: String): Int = row.trim().trim('|').split("|").size
}
