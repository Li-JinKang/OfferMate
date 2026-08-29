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

    private const val MEMO_MAX_ENTRIES = 300

    private val memo = object : LinkedHashMap<String, List<String>>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<String>>): Boolean =
            size > MEMO_MAX_ENTRIES
    }
    private val memoLock = Any()

    /**
     * [blocks] 的记忆化版本，用于**内容固定**的消息（历史消息、定稿消息）。
     *
     * 流式生成中的那条消息每帧内容都不同，**不要**走这里，否则每帧都会塞一条新缓存。
     */
    fun blocksMemo(text: String): List<String> {
        synchronized(memoLock) { memo[text] }?.let { return it }
        val result = blocks(text)
        synchronized(memoLock) { memo[text] = result }
        return result
    }

    private val FENCE_LINE = Regex("^[ \t]*```")
    private val LIST_ITEM = Regex("^[ \t]*([-*+]|\\d+[.)])\\s")
    private val QUOTE_LINE = Regex("^[ \t]*>")

    /**
     * 顶层列表项行：标记必须在第 0 列，且后面得有实际内容。
     *
     * 要求「有内容」是为了避开两处歧义：`-` 单独一行是 setext 标题的下划线，
     * `- ` 这种空项在 CommonMark 里也不能打断段落。
     */
    private val TOP_LEVEL_LIST_ITEM = Regex("^([-*+]|\\d+[.)])\\s+\\S")

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
        for (i in 1 until lines.size) {
            if (inFence[i]) continue
            val prev = lines[i - 1]
            val cut = (prev.isBlank() && !inFence[i - 1] && isSafeBoundary(lines, i - 1)) ||
                isListItemStart(lines, i, inFence)
            if (!cut) continue
            // 片段 = lines[start..i-1]，joinToString 后不含末尾换行，补一个还原出行分隔本身。
            result.add(lines.subList(start, i).joinToString("\n") + "\n")
            start = i
        }
        val tail = lines.subList(start, lines.size).joinToString("\n")
        if (tail.isNotEmpty() || result.isEmpty()) result.add(tail)
        return result
    }

    /**
     * 顶层列表项 [index] 处能否切开——这是**最重要的一刀**。
     *
     * 空行是唯一边界时，一个几十项的列表就是一个 LazyColumn item：里面每项一个 Row、每行两个文本节点，
     * 而渲染库的每个文本节点都要重建 AnnotatedString 并挂上 onPlaced / 图片状态 / inlineContent，
     * 于是整块必须在进入视口的那一帧全部组合测量完，滚动必然掉帧。而 AI 回答里长列表极其常见。
     *
     * 按项切开是安全的：渲染库不区分 loose/tight 列表，序号也取自首个列表项的数字
     * （CommonMark start number），所以 `3. xxx` 单独成块依然显示为 3。间距上每个列表块会多出
     * 一份 `padding.list` 上下留白，把它设为 0 后「拆」与「不拆」的项间距完全一致。
     *
     * 前提是**确认已经在列表内部**：往前找最近的非空行，它必须是列表标记行或缩进续行。
     * 这样就绕开了「列表项能否打断段落」的一堆细则（有序项只有以 1 开头才能打断段落），
     * 也不会切断列表项的惰性续行——续行在第 0 列且不是标记行，此时不切。
     */
    private fun isListItemStart(lines: List<String>, index: Int, inFence: BooleanArray): Boolean {
        if (!TOP_LEVEL_LIST_ITEM.containsMatchIn(lines[index])) return false
        for (j in index - 1 downTo 0) {
            val line = lines[j]
            if (line.isBlank()) continue
            if (inFence[j]) return false
            // 缩进行：列表项的续行或子项，说明这里是列表内部。
            if (line.startsWith(" ") || line.startsWith("\t")) return true
            return LIST_ITEM.containsMatchIn(line)
        }
        return false
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
