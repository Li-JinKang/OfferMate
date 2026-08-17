package com.jk.offermate.agent

/**
 * 一个可被模型调用的工具（本地执行）。参数以 JSON 字符串传入，结果以字符串返回。
 */
interface Tool {
    val spec: ToolSpec
    suspend fun call(argumentsJson: String): String
}

/** 工具注册表：按名字查找、对外提供 specs。 */
class ToolRegistry(tools: List<Tool> = emptyList()) {
    private val byName: Map<String, Tool> = tools.associateBy { it.spec.name }

    fun specs(): List<ToolSpec> = byName.values.map { it.spec }
    fun find(name: String): Tool? = byName[name]
    fun isEmpty(): Boolean = byName.isEmpty()
}
