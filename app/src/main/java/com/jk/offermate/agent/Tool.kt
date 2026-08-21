package com.jk.offermate.agent

/**
 * 一个可被模型调用的工具（本地执行，或经 MCP 客户端转发到外部服务器）。
 * 参数以 JSON 字符串传入，结果以字符串返回。
 */
interface Tool {
    val spec: ToolSpec
    suspend fun call(argumentsJson: String): String
}

/**
 * 工具注册表：按名字查找、对外提供 specs。
 *
 * 工具集**按需解析**（每次访问都重新求值 [provider]），因此支持运行时动态变化的工具——
 * 例如 MCP 服务器异步发现后新增的工具会自动出现在后续的工具轮里，无需重建注册表。
 * 对固定工具集仍可用 `ToolRegistry(listOf(...))` 构造（内部包装为常量 provider）。
 */
class ToolRegistry(private val provider: () -> List<Tool> = { emptyList() }) {

    /** 固定工具集的便捷构造。 */
    constructor(tools: List<Tool>) : this({ tools })

    private fun all(): List<Tool> = provider()

    fun specs(): List<ToolSpec> = all().map { it.spec }
    fun find(name: String): Tool? = all().firstOrNull { it.spec.name == name }
    fun isEmpty(): Boolean = all().isEmpty()
}
