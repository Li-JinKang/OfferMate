package com.jk.offermate.ui.components

import com.mikepenz.markdown.model.Input
import com.mikepenz.markdown.model.MarkdownState
import com.mikepenz.markdown.model.ReferenceLinkHandler
import com.mikepenz.markdown.model.ReferenceLinkHandlerImpl
import com.mikepenz.markdown.model.State
import org.intellij.markdown.MarkdownElementTypes
import org.intellij.markdown.ast.ASTNode
import org.intellij.markdown.ast.getTextInNode
import org.intellij.markdown.flavours.gfm.GFMFlavourDescriptor
import org.intellij.markdown.parser.MarkdownParser

/**
 * Markdown 解析结果缓存：以**原文全文**为 key 缓存解析好的 [State.Success]。
 *
 * 为什么需要它 —— `rememberMarkdownState(content, immediate = true)` 有两个问题：
 *
 * 1. 【每次重组都全量重解析】库的实现是
 *    ```
 *    val state = remember(input) { MarkdownState(input) }
 *    if (immediate) state.parseBlocking()   // ← 不在 remember 里！
 *    ```
 *    `parseBlocking()` 写在组合体里无条件执行，所以**每一次重组**都会在主线程同步重解析整篇
 *    Markdown——不只是内容变化时。流式输出期间每个 token 一次全量解析，直接卡主线程。
 *    （另外 `Input` 是 data class，但它持有的 `parser`/`flavour`/`referenceLinkHandler` 默认值
 *    每次组合都新建、按引用比较，所以 `remember(input)` 其实从不命中。）
 *
 * 2. 【改成异步就有空白帧】`immediate = false` 会把解析挪到 Dispatchers.Default，但初始状态固定是
 *    [State.Loading]（渲染为空白 Box），必然先闪一帧空白再切到 Success。
 *
 * 解法：自己按内容缓存解析结果，**每份内容只解析一次**。
 * - [peek] / [getOrParseBlocking]：同步路径，给没有预热机会的调用方（题目卡片等），
 *   行为与原来的 `immediate = true` 一致，但只在首次付出解析成本。
 * - [warm]：异步路径，给可以提前预热的场景（会话历史）和流式中间态。
 *
 * 用有界 LRU（LinkedHashMap accessOrder=true）避免长对话/多会话下无限增长。
 */
object MarkdownStateCache {

    private const val MAX_ENTRIES = 200

    /** flavour 是无状态的规则描述，可安全共享；parser 每次解析单独建，避免并发共享实例。 */
    private val flavour = GFMFlavourDescriptor()

    private val cache = object : LinkedHashMap<String, State.Success>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, State.Success>): Boolean =
            size > MAX_ENTRIES
    }
    private val lock = Any()

    /** 同步读缓存；不触发任何解析。 */
    fun peek(content: String): State.Success? = synchronized(lock) { cache[content] }

    /**
     * 异步解析（内部走 Dispatchers.Default，不占主线程）；已缓存则直接复用。
     *
     * @param store 是否写入 LRU。**流式中间态必须传 false**：一条 3000 字的回复会产生上千个
     *   递增前缀，全部入缓存会把真实消息整批挤出去，反而让历史消息回滚到每次重组重新解析。
     */
    suspend fun warm(content: String, store: Boolean = true): State {
        peek(content)?.let { return it }
        val handler = ReferenceLinkHandlerImpl()
        val result = MarkdownState(
            Input(
                content = content,
                lookupLinks = true,
                flavour = flavour,
                parser = MarkdownParser(flavour),
                referenceLinkHandler = handler,
            )
        ).parse() // 公开 suspend API，内部 withContext(Dispatchers.Default)，且会完成链接 lookup
        if (store && result is State.Success) put(content, result)
        return result
    }

    /**
     * 命中缓存则直接返回；未命中则**在当前线程同步解析**一次并缓存。
     *
     * 用于没有预热机会的调用方（如题目卡片展开答案）：那里原本用 `immediate = true`，本来就是主线程
     * 同步解析，所以这条路径不会让情况变差——反而从「每次重组都解析」变成「每份内容只解析一次」。
     * 流式中间态**不要**走这里，走 [warm] 的异步路径。
     */
    fun getOrParseBlocking(content: String): State {
        peek(content)?.let { return it }
        val handler = ReferenceLinkHandlerImpl()
        return try {
            val node = MarkdownParser(flavour).buildMarkdownTreeFromString(content)
            // 复刻库内部的引用式链接 lookup（其 lookupLinkDefinition 是 internal，这里用公开 API 自己走一遍）。
            // linksLookedUp 同时决定 `[label]: url` 定义行是否被隐藏，置 true 才与库默认渲染一致。
            storeLinkDefinitions(node, content, handler)
            State.Success(node, content, linksLookedUp = true, referenceLinkHandler = handler)
        } catch (error: Throwable) {
            State.Error(error, handler)
        }.also { if (it is State.Success) put(content, it) }
    }

    private fun put(content: String, state: State.Success) {
        synchronized(lock) { cache[content] = state }
    }

    /**
     * 递归收集 `[label]: destination` 形式的链接定义并注册到 [handler]，
     * 供正文里的 `[text][label]` 解析出地址。
     */
    private fun storeLinkDefinitions(node: ASTNode, content: String, handler: ReferenceLinkHandler) {
        if (node.type == MarkdownElementTypes.LINK_DEFINITION) {
            val label = node.children.firstOrNull { it.type == MarkdownElementTypes.LINK_LABEL }
                ?.getTextInNode(content)?.toString()
            val destination = node.children.firstOrNull { it.type == MarkdownElementTypes.LINK_DESTINATION }
                ?.getTextInNode(content)?.toString()
            if (label != null) handler.store(label.trim('[', ']'), destination)
        }
        node.children.forEach { storeLinkDefinitions(it, content, handler) }
    }
}
