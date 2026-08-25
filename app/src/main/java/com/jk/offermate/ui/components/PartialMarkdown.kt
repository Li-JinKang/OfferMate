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
            return if (text.endsWith("\n")) text + "```" else text + "\n```"
        }

        // 2) 行内标记只在**最后一个块**内平衡：Markdown 的行内解析以块为单位，
        //    改动更早的块既没必要也有风险。
        val cut = text.lastIndexOf("\n\n")
        val head = if (cut < 0) "" else text.substring(0, cut + 2)
        var tail = if (cut < 0) text else text.substring(cut + 2)

        // 尾块里还有 ``` 说明它落在代码块内部（空行出现在围栏之间），
        // 此时任何行内标记的增删都可能破坏围栏，直接跳过。
        if (!tail.contains("```")) {
            tail = dropIncompleteTableDelimiter(tail)
            INLINE_MARKERS.forEach { tail = balance(tail, it) }
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
     * 表头已经写完、分隔行（`|---|---|`）还没写完时，GFM 还不认它是表格，
     * 会先把这两行当普通段落渲染，等分隔行补全再整体变表格。
     * 这里把没写完的分隔行暂时摘掉，等结构成形再一次性渲染成表格。
     */
    private fun dropIncompleteTableDelimiter(text: String): String {
        val lines = text.split("\n")
        val last = lines.last().trim()
        if (last.length >= 1 && last.startsWith("|") &&
            last.all { it == '|' || it == '-' || it == ':' || it == ' ' }
        ) {
            return lines.dropLast(1).joinToString("\n")
        }
        return text
    }
}
