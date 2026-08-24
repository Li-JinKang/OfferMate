package com.jk.offermate.agent.tool

/**
 * 本地工具：列出候选人题库中的所有**分类**（考点大类）。
 *
 * 让模型先了解题库已有的知识板块，再决定检索方向或归类建议。
 * 通过 [categoriesProvider] 注入数据来源，与存储解耦、便于测试。
 */
class CategoryListTool(
    private val categoriesProvider: suspend () -> List<String>
) : Tool {

    override val spec = ToolSpec(
        name = "list_categories",
        description = "列出候选人题库中已有的题目分类（考点大类）。用于了解题库覆盖的知识板块，" +
            "或在检索/归类前先掌握已有类目。无参数。"
    )

    override suspend fun call(argumentsJson: String): String {
        val categories = categoriesProvider().map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        if (categories.isEmpty()) return "题库中暂无分类。"
        return "题库现有分类（共 ${categories.size} 个）：\n" +
            categories.joinToString("\n") { "- $it" }
    }
}
