package com.jk.offermate.agent

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.util.UUID

/**
 * 内联工具调用解析：兜底解析**以文本形式**混在 assistant 内容里的工具调用标记。
 *
 * 背景：部分 OpenAI 兼容服务/模型（尤其经代理转发的 Claude 系）并不把工具调用放进结构化的
 * `tool_calls` 字段，而是把类似
 *
 * ```
 * <tool_calls><invoke name="load_project_detail">
 *   <parameter name="profileId">android</parameter>
 * </invoke></tool_calls>
 * ```
 *
 * 的 XML/伪标记直接吐进 `message.content`。这会导致 [DeepSeekClient.parseTurn] 把整段标记当成最终
 * 文本回传，最终在对话里显示成一堆原始尖括号（见线上截图）。
 *
 * 本解析器对命名空间前缀（如 `antml:`）、大小写、单/双引号都做了容忍，只要能识别出 `invoke`/`parameter`
 * 结构就抽取成 [ToolCall]；同时提供 [strip] 从最终展示文本里剔除这些标记，保证即便未被当作工具调用执行，
 * 用户也不会看到裸标记。
 */
object InlineToolCallParser {

    // 容忍任意命名空间前缀（如 antml:）、单双引号；DOTALL 让参数值可跨行。
    private val INVOKE = Regex(
        "<[^>]*?invoke\\s+name\\s*=\\s*[\"']([^\"']+)[\"'][^>]*?>(.*?)</[^>]*?invoke\\s*>",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    )
    private val PARAMETER = Regex(
        "<[^>]*?parameter\\s+name\\s*=\\s*[\"']([^\"']+)[\"'][^>]*?>(.*?)</[^>]*?parameter\\s*>",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    )
    // 用于剔除包裹标签与残余单标签（<tool_calls> / </invoke> / 自闭合等）。
    private val ANY_TOOL_TAG = Regex(
        "</?[^>]*?(tool_calls|invoke|parameter)\\b[^>]*?>",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    )

    /** 内容里是否疑似包含内联工具调用标记。 */
    fun containsMarkup(content: String): Boolean =
        content.contains("invoke", ignoreCase = true) && INVOKE.containsMatchIn(content)

    /**
     * 从内容里解析出全部内联工具调用（无则返回空）。参数一律按字符串放进 arguments JSON。
     */
    fun parse(content: String): List<ToolCall> =
        INVOKE.findAll(content).mapNotNull { m ->
            val name = m.groupValues[1].trim().takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            val body = m.groupValues[2]
            val args = buildJsonObject {
                PARAMETER.findAll(body).forEach { p ->
                    val key = p.groupValues[1].trim()
                    if (key.isNotEmpty()) put(key, p.groupValues[2].trim())
                }
            }
            ToolCall(id = "inline_${UUID.randomUUID()}", name = name, argumentsJson = args.toString())
        }.toList()

    /**
     * 从最终展示文本里剔除内联工具调用标记，返回干净正文。
     * 若剔除后为空（整段都是标记），返回空串，交由上层兜底。
     */
    fun strip(content: String): String {
        if (!content.contains('<')) return content
        // 先整体去掉每个 <invoke>...</invoke> 块，再清理残余的包裹/单标签。
        var out = INVOKE.replace(content, "")
        out = ANY_TOOL_TAG.replace(out, "")
        return out.trim()
    }
}
