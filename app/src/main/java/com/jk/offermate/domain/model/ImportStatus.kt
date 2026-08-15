package com.jk.offermate.domain.model

/** 导入任务状态机（见 docs/plan/ui-and-runtime.md）。 */
enum class ImportStatus(val label: String) {
    PENDING("等待中"),
    READING("读取中"),
    ANALYZING("分析中"),
    DONE("已完成"),
    NEEDS_MANUAL_INPUT("需手动粘贴"),
    FAILED("失败");

    val isTerminal: Boolean get() = this == DONE || this == FAILED || this == NEEDS_MANUAL_INPUT

    companion object {
        fun from(name: String?): ImportStatus = entries.firstOrNull { it.name == name } ?: PENDING
    }
}
