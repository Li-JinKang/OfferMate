package com.jk.offermate.agent.resume

/**
 * 简历画像（分析上下文）。相关性筛选与作答都以当前激活方向的画像为依据。
 *
 * @param targetRole 目标岗位，如 "Android 开发"
 * @param skills     技能栈
 * @param yearsOfExperience 工作年限（可空）
 * @param projects   项目关键词
 * @param rawText    简历原始文本（兜底，供模型参考）
 */
data class ResumeProfile(
    val targetRole: String,
    val skills: List<String> = emptyList(),
    val yearsOfExperience: Int? = null,
    val projects: List<String> = emptyList(),
    val rawText: String = ""
)
