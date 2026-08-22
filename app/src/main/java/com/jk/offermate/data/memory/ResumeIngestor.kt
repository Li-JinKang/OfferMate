package com.jk.offermate.data.memory

import com.jk.offermate.agent.ProfileMatcher
import com.jk.offermate.agent.ResumeDetail
import com.jk.offermate.agent.ResumeStructurer
import com.jk.offermate.agent.StructuredResume

/**
 * 简历落地：把简历原文结构化、判定方向，并写入分层文件记忆。
 *
 * 只操作记忆系统（[MemoryStore]），**不触碰题目、不重算相关性**（双系统解耦原则）。
 * 流程：结构化 → ProfileMatcher 判定 → 命中则覆盖更新该记忆集，未命中则新建一份共存。
 */
class ResumeIngestor(
    private val structurer: ResumeStructurer,
    private val matcher: ProfileMatcher,
    private val store: MemoryStore
) {

    /**
     * @param profileId 落地到的记忆集 id
     * @param isNew     true=新建方向；false=并入已有方向（覆盖更新）
     */
    data class Result(val profileId: String, val isNew: Boolean)

    suspend fun ingest(rawText: String): Result {
        val structured = structurer.structure(rawText)
        val existing = store.listProfiles()
        val match = matcher.match(structured.targetRole, existing)

        val isNew = match.matchedId == null
        val profileId = match.matchedId
            ?: MemoryIds.unique(
                base = structured.suggestedId.ifBlank { structured.name.ifBlank { structured.targetRole } },
                taken = existing.map { it.id }.toSet()
            )

        // 概览（L2）
        store.writeProfileOverview(profileId, buildOverview(structured))

        // 细节（L3）
        writeDetails(profileId, DetailKind.PROJECT, structured.projects)
        writeDetails(profileId, DetailKind.EXPERIENCE, structured.experiences)

        // 跨方向共享事实
        if (structured.globalFacts.isNotEmpty()) {
            store.writeGlobal(buildGlobal(structured.globalFacts))
        }

        // 索引条目（新建则追加，命中则覆盖）
        store.upsertProfile(
            MemoryProfileEntry(
                id = profileId,
                name = structured.name,
                targetRole = structured.targetRole,
                summary = structured.summary
            )
        )
        return Result(profileId, isNew)
    }

    private suspend fun writeDetails(profileId: String, kind: DetailKind, items: List<ResumeDetail>) {
        val used = HashSet<String>()
        items.forEach { item ->
            val itemId = MemoryIds.unique(
                base = item.id.ifBlank { item.title.ifBlank { kind.dirName } },
                taken = used
            )
            used += itemId
            store.writeDetail(profileId, kind, itemId, buildDetail(item))
        }
    }

    private fun buildOverview(s: StructuredResume): String = buildString {
        append("# ").append(s.name.ifBlank { s.targetRole }).append('\n')
        append("目标岗位：").append(s.targetRole).append("\n\n")
        if (s.skills.isNotEmpty()) {
            append("## 技能\n")
            s.skills.forEach { append("- ").append(it).append('\n') }
            append('\n')
        }
        if (s.projects.isNotEmpty()) {
            append("## 项目（可下钻 load_project_detail）\n")
            s.projects.forEach { append("- ").append(it.title).append("：").append(it.brief).append('\n') }
            append('\n')
        }
        if (s.experiences.isNotEmpty()) {
            append("## 经历（可下钻 load_experience_detail）\n")
            s.experiences.forEach { append("- ").append(it.title).append("：").append(it.brief).append('\n') }
        }
    }.trimEnd()

    private fun buildDetail(d: ResumeDetail): String = buildString {
        append("# ").append(d.title).append('\n')
        if (d.brief.isNotBlank()) append('\n').append(d.brief).append('\n')
        if (d.detail.isNotBlank()) append('\n').append(d.detail).append('\n')
    }.trimEnd()

    private fun buildGlobal(facts: List<String>): String = buildString {
        append("# 通用事实（跨方向共享）\n")
        facts.forEach { append("- ").append(it).append('\n') }
    }.trimEnd()
}
