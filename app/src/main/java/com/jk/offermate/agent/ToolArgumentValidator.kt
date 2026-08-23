package com.jk.offermate.agent

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * 工具调用的确定性护栏：在真正执行工具前，校验模型传来的参数是否满足工具自身声明的
 * JSON Schema（目前仅做 `required` 字段的存在性/非空校验，不做全量 Schema 校验）。
 *
 * 这是"硬约束"而非提示词层的"软约束"——不管模型是否理解/记住了参数要求，
 * 缺参数就在本地拦截并把明确的错误信息回填给模型，让它能据此重试，而不是让工具带着
 * 缺失参数执行、静默产生错误结果或裸异常。
 */
internal object ToolArgumentValidator {

    /**
     * 校验参数。
     * @return 校验失败时返回可读的错误说明（用于回填给模型）；通过校验返回 null。
     *
     * 设计上偏宽松：
     * - 工具未声明 parametersJson（空字符串）→ 视为无需校验，直接通过；
     * - 工具声明的 schema 本身无法解析 → 不因为"我方 schema 写错"而拦截调用，直接通过；
     * - 只校验 `required` 列出的字段是否存在且非空（非 null、非空字符串）。
     */
    fun validate(spec: ToolSpec, argumentsJson: String): String? {
        if (spec.parametersJson.isBlank()) return null
        val schema = runCatching {
            JsonSupport.json.parseToJsonElement(spec.parametersJson).jsonObject
        }.getOrNull() ?: return null

        val required = (schema["required"] as? JsonArray)
            ?.mapNotNull { it.jsonPrimitive.contentOrNull }
            ?.filter { it.isNotBlank() }
            ?: emptyList()
        if (required.isEmpty()) return null

        val args: JsonObject = runCatching {
            if (argumentsJson.isBlank()) JsonObject(emptyMap())
            else JsonSupport.json.parseToJsonElement(argumentsJson).jsonObject
        }.getOrElse { e ->
            return "参数不是合法 JSON：${e.message ?: "解析失败"}"
        }

        val missing = required.filter { key ->
            when (val value = args[key]) {
                null, is JsonNull -> true
                is JsonPrimitive -> value.contentOrNull.isNullOrBlank()
                else -> false
            }
        }
        if (missing.isEmpty()) return null

        return "缺少必填参数：${missing.joinToString("、")}（工具 ${spec.name} 需要：${required.joinToString("、")}）"
    }
}
