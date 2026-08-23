package com.jk.offermate.agent

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * 流式 tool_calls 增量拼接：OpenAI 兼容流会把一次工具调用拆成多个 delta 片段
 * （首个片段带 index/id/function.name，后续片段仅追加 function.arguments）。
 * 按 index 归并，最终产出完整的 [ToolCall] 列表。
 */
class ToolCallAccumulator {
    private class Partial(var id: String = "", var name: String = "", val args: StringBuilder = StringBuilder())

    private val byIndex = LinkedHashMap<Int, Partial>()

    /** 吃进一个 tool_calls 数组元素（单个 delta 片段）。 */
    fun accumulate(element: JsonObject) {
        val index = element["index"]?.jsonPrimitive?.intOrNull ?: 0
        val partial = byIndex.getOrPut(index) { Partial() }
        element["id"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotEmpty() }?.let { partial.id = it }
        val fn = element["function"]?.jsonObject
        fn?.get("name")?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotEmpty() }?.let { partial.name = it }
        fn?.get("arguments")?.jsonPrimitive?.contentOrNull?.let { partial.args.append(it) }
    }

    /** 产出累积到的工具调用；无则返回 null。 */
    fun build(): List<ToolCall>? {
        if (byIndex.isEmpty()) return null
        return byIndex.values
            .filter { it.name.isNotEmpty() }
            .map { p ->
                ToolCall(
                    id = p.id.ifEmpty { "stream_${p.name}" },
                    name = p.name,
                    argumentsJson = p.args.toString().ifBlank { "{}" }
                )
            }
    }
}
