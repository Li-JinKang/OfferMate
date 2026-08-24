package com.jk.offermate.agent.tool

import com.jk.offermate.agent.JsonSupport
import com.jk.offermate.data.memory.DetailKind
import com.jk.offermate.data.memory.MemoryStore
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * 分级记忆 tool 族：把简历记忆按需暴露给 AI，逐层下钻（L1→L2→L3），避免一次灌入全部记忆。
 *
 * - L1 [ListMemoryProfilesTool]：有哪些方向记忆，供 AI 挑相关的
 * - L2 [LoadProfileOverviewTool]：某方向的技能 + 项目/经历概览（可 query 过滤）
 * - L3 [LoadProjectDetailTool] / [LoadExperienceDetailTool]：下钻具体项目/经历全文
 *
 * 全部只读 [MemoryStore]，与题目工具平等注册进共享 ToolRegistry。
 */

/** 构造分级记忆工具集，便于并入 ToolRegistry。 */
fun memoryTools(store: MemoryStore): List<Tool> = listOf(
    ListMemoryProfilesTool(store),
    LoadProfileOverviewTool(store),
    LoadProjectDetailTool(store),
    LoadExperienceDetailTool(store)
)

/** 解析参数中的某个字符串字段。 */
private fun argString(argumentsJson: String, key: String): String? = runCatching {
    JsonSupport.json.parseToJsonElement(argumentsJson)
        .jsonObject[key]?.jsonPrimitive?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }
}.getOrNull()

/** L1：列出全部方向记忆（id/name/岗位/简述），供 AI 选择相关的一份。 */
class ListMemoryProfilesTool(private val store: MemoryStore) : Tool {
    override val spec = ToolSpec(
        name = "list_memory_profiles",
        description = "读取候选人本人的简历记忆入口。这里存放着候选人的**简历信息**——按求职方向组织" +
            "（如 Java 后端、Android），每份含技能、项目、工作/实习经历等。凡是涉及候选人本人的简历、" +
            "背景、技能、项目、经历、求职方向，或需要“结合我的简历/项目”作答、点评简历、给改进建议时，" +
            "都应**先调用本工具**看有哪些方向记忆，再用 load_profile_overview 加载相关一份的概览。无参数。",
        parametersJson = """{"type":"object","properties":{}}"""
    )

    override suspend fun call(argumentsJson: String): String {
        val profiles = store.listProfiles()
        if (profiles.isEmpty()) return "（候选人尚无任何简历记忆）"
        return profiles.joinToString("\n") { p ->
            "- id=${p.id}｜${p.name}｜岗位：${p.targetRole}" +
                (if (p.summary.isNotBlank()) "｜${p.summary}" else "")
        }
    }
}

/** L2：加载某方向的概览（profile.md）+ 跨方向共享事实（global.md）。可传 query 只回相关行。 */
class LoadProfileOverviewTool(
    private val store: MemoryStore,
    private val maxChars: Int = 4000
) : Tool {
    override val spec = ToolSpec(
        name = "load_profile_overview",
        description = "加载候选人某个求职方向的**简历概览**：技能清单 + 项目/经历简介，附带通用事实（年限/学历等）。" +
            "这是了解候选人简历内容的主要入口。profileId 取自 list_memory_profiles。可传 query 关键词仅返回相关行。" +
            "若要点评/改进某个具体项目或经历，再用 load_project_detail / load_experience_detail 下钻全文。",
        parametersJson = """
            {"type":"object","properties":{
              "profileId":{"type":"string","description":"方向记忆 id"},
              "query":{"type":"string","description":"可选关键词，仅返回相关行"}
            },"required":["profileId"]}
        """.trimIndent()
    )

    override suspend fun call(argumentsJson: String): String {
        val profileId = argString(argumentsJson, "profileId")
            ?: return "（缺少参数 profileId）"
        val overview = store.readProfileOverview(profileId)
            ?: return "（未找到方向记忆：$profileId，请先用 list_memory_profiles 确认 id）"
        val global = store.readGlobal()

        val combined = buildString {
            append(overview)
            if (!global.isNullOrBlank()) append("\n\n").append(global)
        }
        val query = argString(argumentsJson, "query")
        val result = if (query.isNullOrBlank()) combined else {
            val hits = combined.lines().filter { it.contains(query, ignoreCase = true) }
            if (hits.isEmpty()) combined else hits.joinToString("\n")
        }
        return result.take(maxChars)
    }
}

/** L3：下钻某方向下的具体项目全文。 */
class LoadProjectDetailTool(private val store: MemoryStore) :
    DetailTool(store, DetailKind.PROJECT, "load_project_detail", "projectId", "项目") {
    override val spec = detailSpec()
}

/** L3：下钻某方向下的具体经历全文。 */
class LoadExperienceDetailTool(private val store: MemoryStore) :
    DetailTool(store, DetailKind.EXPERIENCE, "load_experience_detail", "experienceId", "经历") {
    override val spec = detailSpec()
}

/** 项目/经历细节下钻的共同实现（仅子目录与参数名不同）。 */
abstract class DetailTool(
    private val store: MemoryStore,
    private val kind: DetailKind,
    private val toolName: String,
    private val itemIdParam: String,
    private val label: String
) : Tool {

    protected fun detailSpec() = ToolSpec(
        name = toolName,
        description = "加载候选人简历里某个${label}的详细内容（背景/职责/技术栈/难点/亮点），用于深入点评或结合作答。" +
            "profileId 取自 list_memory_profiles；$itemIdParam 必须取自 load_profile_overview 概览里各条目前缀的 " +
            "**id= 值**（例如 id=vibeplayer 就传 vibeplayer），不要用中文标题或公司全名。",
        parametersJson = """
            {"type":"object","properties":{
              "profileId":{"type":"string","description":"方向记忆 id"},
              "$itemIdParam":{"type":"string","description":"${label} id"}
            },"required":["profileId","$itemIdParam"]}
        """.trimIndent()
    )

    override suspend fun call(argumentsJson: String): String {
        val profileId = argString(argumentsJson, "profileId") ?: return "（缺少参数 profileId）"
        val itemId = argString(argumentsJson, itemIdParam) ?: return "（缺少参数 $itemIdParam）"
        // 容错解析：传入的可能是真实 id，也可能是显示名/大小写不一致，统一解析成实际文件 id。
        val resolved = runCatching { store.resolveDetailId(profileId, kind, itemId) }.getOrNull()
        if (resolved != null) {
            store.readDetail(profileId, kind, resolved)?.let { return it }
        }
        val available = runCatching { store.listDetails(profileId, kind) }.getOrDefault(emptyList())
        val hint = if (available.isEmpty()) "该方向暂无${label}记录" else "可用 id：${available.joinToString("、")}"
        return "（未找到${label}：$itemId。$hint。请使用 load_profile_overview 概览里各条目的 id= 值）"
    }
}
