package com.jk.offermate.ui.components

/**
 * 流式文本的**按块切分**：把整篇拆成若干块级片段，最后一段是正在生成的。
 *
 * 为什么需要 —— 打字机把 Markdown 重解析压到了每帧最多一次，但整篇当成一个 [MarkdownText] 渲染时，
 * 每帧仍要重解析全文、重建整棵组合树、重新测量整个子树。一条 3000 字的回答越写到后面越贵。
 *
 * 拆成块之后：
 * - 已经写完的块内容永不再变，各自命中 [MarkdownStateCache]，组合树和布局都保持稳定；
 * - 每帧只有最后一块（通常几十到几百字）重解析、重测量；
 * - 又写完一块时只是**追加**一个 composable，前面的块不受影响。
 *
 * （曾经用「稳定前缀 + 尾巴」两段式，问题是前缀每前进一块就换一份新 AST，
 * 导致整个前缀子树全量重组 + 全量重新测量，越写越卡。）
 *
 * 切分规则刻意**保守**：宁可少切一刀（退化成整篇渲染，行为与优化前一致），
 * 也不能切出错误的块结构。
 */
object StreamingMarkdown {

    /** 短文本不切：整篇解析本来就便宜，切开反而多出若干次组合和缓存写入。 */
    private const val MIN_SPLIT_LENGTH = 200

    private val FENCE_LINE = Regex("^[ \t]*```")
    private val LIST_ITEM = Regex("^[ \t]*([-*+]|\\d+[.)])\\s")
    private val QUOTE_LINE = Regex("^[ \t]*>")

    /** 一行所属的块类型，用于判断能否在两块之间下刀。 */
    private enum class BlockKind { LIST, QUOTE, OTHER }

    /**
     * 按安全的块级边界切分 [text]。
     *
     * 保证 `blocks(text).joinToString("") == text`——任何情况下都不会漏内容或改内容。
     *
     * @return 至少含一个元素；最后一个元素是「正在生成中」的那一段。
     */
    fun blocks(text: String): List<String> {
        if (text.length < MIN_SPLIT_LENGTH) return listOf(text)
        val lines = text.split("\n")
        if (lines.size < 3) return listOf(text)

        // 标记每行是否处于代码围栏内（围栏行本身也算，防止贴着围栏切开）。
        val inFence = BooleanArray(lines.size)
        var fenceOpen = false
        for (i in lines.indices) {
            val isFence = FENCE_LINE.containsMatchIn(lines[i])
            inFence[i] = fenceOpen || isFence
            if (isFence) fenceOpen = !fenceOpen
        }
        // 围栏没闭合说明尾部整段都在代码块里，任何切分都不安全。
        if (fenceOpen) return listOf(text)

        val result = ArrayList<String>()
        var start = 0
        // 最后一行不作为边界：它属于正在生成的那一段。
        for (i in 0 until lines.lastIndex) {
            if (lines[i].isNotBlank() || inFence[i]) continue
            if (!isSafeBoundary(lines, i)) continue
            // 片段 = lines[start..i]，joinToString 后天然以 "\n" 收尾，再补一个还原出空行本身。
            result.add(lines.subList(start, i + 1).joinToString("\n") + "\n")
            start = i + 1
        }
        val tail = lines.subList(start, lines.size).joinToString("\n")
        if (tail.isNotEmpty() || result.isEmpty()) result.add(tail)
        return result
    }

    /**
     * 判断空行 [blankIndex] 处能否切开。
     *
     * 拒绝的情况：
     * - 下一行是缩进续行：可能是列表项的第二段或缩进代码块，切开会丢掉缩进语义。
     * - 边界两侧属于**同一种**列表/引用块：Markdown 里中间夹空行的列表仍是一个列表（loose list），
     *   切开会变成两个列表，序号和间距都可能变。
     */
    private fun isSafeBoundary(lines: List<String>, blankIndex: Int): Boolean {
        var next: String? = null
        for (i in blankIndex + 1 until lines.size) {
            if (lines[i].isNotBlank()) {
                next = lines[i]
                break
            }
        }
        if (next == null) return false
        if (next.startsWith("    ") || next.startsWith("\t")) return false

        val nextKind = kindOf(next)
        if (nextKind == BlockKind.OTHER) return true

        var prev: String? = null
        for (i in blankIndex - 1 downTo 0) {
            if (lines[i].isNotBlank()) {
                prev = lines[i]
                break
            }
        }
        if (prev == null) return false
        return kindOf(prev) != nextKind
    }

    private fun kindOf(line: String): BlockKind = when {
        LIST_ITEM.containsMatchIn(line) -> BlockKind.LIST
        QUOTE_LINE.containsMatchIn(line) -> BlockKind.QUOTE
        else -> BlockKind.OTHER
    }
}
