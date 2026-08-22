package com.jk.offermate.data.memory

/**
 * 记忆集索引条目（对应 index.json 中的一项）。
 *
 * 供 L1（list_memory_profiles）让 AI 快速判断哪份记忆与当前题目相关，
 * 而无需读取任何记忆集的具体内容。
 *
 * @param id        记忆集稳定标识，同时作为文件夹名（已做安全校验）
 * @param name      展示名，如 "Java 后端"
 * @param targetRole 目标岗位，供 ProfileMatcher 语义匹配
 * @param summary   AI 生成的一句话简述
 */
data class MemoryProfileEntry(
    val id: String,
    val name: String,
    val targetRole: String,
    val summary: String = ""
)

/**
 * 细节文件的类别，决定其在记忆集文件夹内的子目录。
 * 用于 L3 下钻（load_project_detail / load_experience_detail）。
 */
enum class DetailKind(val dirName: String) {
    PROJECT("projects"),
    EXPERIENCE("experiences")
}
