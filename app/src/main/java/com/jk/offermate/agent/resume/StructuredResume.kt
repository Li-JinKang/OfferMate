package com.jk.offermate.agent.resume

/**
 * 简历经 AI 结构化后的产物（记忆内容真源）。
 *
 * 由 [ResumeStructurer] 从简历原文解析得到，随后由上层写入分层文件：
 * - [name]/[targetRole]/[summary] → index.json 条目
 * - [skills] + 各 detail 的 brief → profile.md（L2 概览）
 * - [projects]/[experiences] 的 detail → projects 与 experiences 下的细节文件（L3）
 * - [globalFacts] → global.md（跨方向共享）
 *
 * @param suggestedId 模型建议的英文 slug（如 "java-backend"），仅作新建时的 id 候选
 */
data class StructuredResume(
    val suggestedId: String,
    val name: String,
    val targetRole: String,
    val summary: String,
    val skills: List<String> = emptyList(),
    val globalFacts: List<String> = emptyList(),
    val projects: List<ResumeDetail> = emptyList(),
    val experiences: List<ResumeDetail> = emptyList()
)

/**
 * 一个可下钻的细节条目（项目或经历）。
 *
 * @param id     细节文件名 slug（如 "order-system"）
 * @param title  展示标题
 * @param brief  一句话摘要，进入 profile.md 概览
 * @param detail 全文，写入独立细节文件供 L3 按需加载
 */
data class ResumeDetail(
    val id: String,
    val title: String,
    val brief: String,
    val detail: String
)
