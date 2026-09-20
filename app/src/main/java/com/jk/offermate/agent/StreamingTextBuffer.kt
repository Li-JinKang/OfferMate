package com.jk.offermate.agent

/**
 * 流式文本安全缓冲：累积模型的 content 增量，只把“确定不属于工具调用标记”的前缀通过回调透出，
 * 避免把正在形成的内联工具标记（<invoke .../>）逐字闪现给用户。
 *
 * 策略：
 * - 一旦累积内容里出现**标签形态**的工具标记（`<invoke` / `<tool_calls` / `</invoke` …，
 *   容忍命名空间前缀与前导空白），判定本轮疑似是文本式工具调用，进入**静默模式**：
 *   不再回调任何文本（最终交由 [com.jk.offermate.agent.tool.InlineToolCallParser] 解析成工具调用）。
 * - 否则，若结尾存在一个尚未闭合的 `<`（其后还没有 `>`），则暂缓 `<` 起的这一小段（可能正在形成标签），
 *   待其闭合或流结束再输出；其余部分正常回调。普通文本里的 `<`（如泛型、比较符）只会短暂延迟。
 *
 * ## 两处刻意的实现细节
 *
 * **1）判定必须是标签形态，不能是裸词。** 早先的实现是
 * `sb.contains("invoke") || sb.contains("tool_calls")`，只要回答正文里出现 `invoke` 这个词
 * 就整段静默——而这是个面经 App，"反射 `Method.invoke`"、"function calling 的 tool_calls 字段"
 * 正是高频考点。命中后的表现不是报错，而是：转圈很久 → 整段答案突然出现 → 打字机因积压巨大
 * 触发加速追赶，掉帧尖峰。所以判定收紧到必须带 `<`。
 *
 * **2）扫描必须是增量的。** 判定与 `safeEmitEnd()` 原来都对整个 buffer 重扫，而 chunk 到达
 * 20~50 次/秒、一篇长回答有上千个 chunk，累计就是 O(n²) 次字符比较，压在 SSE 读取的 IO 线程上。
 * 这里只扫**新增窗口**（尾部保留一个标记长度的重叠区，防止标记正好被 chunk 边界切开），
 * 并把「最后一个未闭合 `<` 的位置」增量维护。
 */
class StreamingTextBuffer(private val onDelta: (String) -> Unit) {
    private val sb = StringBuilder()
    private var emitted = 0
    private var silenced = false

    /** 已经扫过工具标记的前缀长度（含重叠区回退，见 [scanForToolMarkup]）。 */
    private var scanned = 0

    /**
     * 最后一个「其后还没出现 `>`」的 `<` 的下标；-1 表示当前没有悬空的 `<`。
     * 增量维护，避免每个 chunk 都 `lastIndexOf`。
     */
    private var openLt = -1

    /** 追加一段新增量，按策略回调可安全输出的部分。 */
    fun append(delta: String) {
        if (delta.isEmpty()) return
        val from = sb.length
        sb.append(delta)
        updateOpenLt(from)
        if (!silenced && scanForToolMarkup()) {
            silenced = true
            return
        }
        if (silenced) return
        val safeEnd = if (openLt >= 0) openLt else sb.length
        if (safeEnd > emitted) {
            onDelta(sb.substring(emitted, safeEnd))
            emitted = safeEnd
        }
    }

    /** 流结束：返回累积的完整原始文本（供上层判断/解析工具标记）。 */
    fun finish(): String = sb.toString()

    /** 疑似工具调用而被静默。 */
    fun isSilenced(): Boolean = silenced

    /** 把尚未回调的剩余安全文本一次性补齐（仅在确认不是工具调用时调用）。 */
    fun flushRemaining() {
        if (silenced) return
        if (sb.length > emitted) {
            onDelta(sb.substring(emitted))
            emitted = sb.length
        }
    }

    /** 只扫新增的那一段，维护「最后一个悬空 `<`」的位置。 */
    private fun updateOpenLt(from: Int) {
        for (i in from until sb.length) {
            when (sb[i]) {
                '<' -> openLt = i
                '>' -> openLt = -1
            }
        }
    }

    /**
     * 在新增窗口里找标签形态的工具标记。
     *
     * 窗口起点回退 [OVERLAP] 个字符：标记可能正好被 chunk 边界切成两半
     * （`<inv` + `oke name=…`），不留重叠区就会漏判。[OVERLAP] 取最长待匹配标记的长度即可。
     */
    private fun scanForToolMarkup(): Boolean {
        val start = (scanned - OVERLAP).coerceAtLeast(0)
        val window = sb.substring(start)
        scanned = sb.length
        return TOOL_MARKUP.containsMatchIn(window)
    }

    private companion object {
        /**
         * 标签形态的工具标记。容忍 `<` 后的空白与任意命名空间前缀（如 `<invoke`），
         * 与 [com.jk.offermate.agent.tool.InlineToolCallParser] 的宽松匹配保持一致。
         */
        private val TOOL_MARKUP = Regex("</?\\s*[\\w.:-]*(invoke|tool_calls)\\b", RegexOption.IGNORE_CASE)

        /** 重叠窗口长度：够覆盖 `</tool_calls` 这类最长前缀即可。 */
        private const val OVERLAP = 32
    }
}
