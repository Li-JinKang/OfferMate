package com.jk.offermate.agent

/**
 * 流式文本安全缓冲：累积模型的 content 增量，只把“确定不属于工具调用标记”的前缀通过回调透出，
 * 避免把正在形成的内联工具标记（<invoke .../>）逐字闪现给用户。
 *
 * 策略：
 * - 一旦累积内容里出现 `invoke` / `tool_calls`（忽略大小写），判定本轮疑似是文本式工具调用，
 *   进入**静默模式**：不再回调任何文本（最终交由 [InlineToolCallParser] 解析成工具调用）。
 * - 否则，若结尾存在一个尚未闭合的 `<`（其后还没有 `>`），则暂缓 `<` 起的这一小段（可能正在形成标签），
 *   待其闭合或流结束再输出；其余部分正常回调。普通文本里的 `<`（如泛型、比较符）只会短暂延迟。
 */
class StreamingTextBuffer(private val onDelta: (String) -> Unit) {
    private val sb = StringBuilder()
    private var emitted = 0
    private var silenced = false

    /** 追加一段新增量，按策略回调可安全输出的部分。 */
    fun append(delta: String) {
        if (delta.isEmpty()) return
        sb.append(delta)
        if (!silenced && looksLikeToolMarkup()) {
            silenced = true
            return
        }
        if (silenced) return
        val safeEnd = safeEmitEnd()
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

    private fun looksLikeToolMarkup(): Boolean =
        sb.contains("invoke", ignoreCase = true) || sb.contains("tool_calls", ignoreCase = true)

    private fun safeEmitEnd(): Int {
        val lastLt = sb.lastIndexOf("<")
        if (lastLt < 0) return sb.length
        // 最后一个 '<' 之后若还没出现 '>'，说明可能是未闭合标签，暂缓这一段。
        val hasClose = sb.indexOf(">", lastLt) >= 0
        return if (hasClose) sb.length else lastLt
    }
}
