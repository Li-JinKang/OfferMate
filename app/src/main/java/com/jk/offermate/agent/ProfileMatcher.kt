package com.jk.offermate.agent

import com.jk.offermate.data.memory.MemoryProfileEntry
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * 方向匹配：给定新简历推断出的目标岗位与已有记忆集清单，由 LLM 语义判断
 * 应**并入某个已有记忆集**（同一求职方向）还是**新建**一份。
 *
 * 与题目分类同构（复用 CategoryClassifier 套路）：方向也是数据，由模型判定，而非写死规则。
 * 纯逻辑 [buildMessages]/[parse] 可单测；[match] 做一次 LLM 调用。
 */
class ProfileMatcher(private val aiClient: AiClient) {

    fun buildMessages(targetRole: String, existing: List<MemoryProfileEntry>): List<ChatMessage> {
        val list = existing.joinToString("\n") { "- id=${it.id}｜${it.name}｜岗位：${it.targetRole}" }
        val system = """
            你负责判断一份新简历应归入哪一份已有"求职方向记忆"，还是新建。
            规则：
            1. 若新简历的目标岗位与某个已有方向**本质相同**（如都是 Java 后端，仅措辞不同），返回该方向的 id。
            2. 若是**明显不同的方向**（如从 Java 后端转 Android），返回 null（表示新建）。
            3. 只依据"求职方向/岗位性质"判断，不受用工年限、公司等细节影响。
            只输出 JSON：{"matchedId":"已有id或null","reason":"简要理由"}
        """.trimIndent()
        val user = buildString {
            append("已有方向：\n")
            append(if (existing.isEmpty()) "（暂无）" else list)
            append("\n\n新简历目标岗位：").append(targetRole)
        }
        return listOf(ChatMessage(Role.SYSTEM, system), ChatMessage(Role.USER, user))
    }

    /** 解析匹配结果。matchedId 为 null/空/未知 id 时视为"新建"。 */
    fun parse(raw: String, existing: List<MemoryProfileEntry>): ProfileMatch {
        val jsonText = JsonSupport.extractJsonBlock(raw) ?: return ProfileMatch(null)
        val obj = runCatching { JsonSupport.json.parseToJsonElement(jsonText) }
            .getOrNull() as? JsonObject ?: return ProfileMatch(null)
        val reason = obj["reason"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
        val id = obj["matchedId"]?.jsonPrimitive?.contentOrNull?.trim()
        val matched = id?.takeIf { it.isNotEmpty() && !it.equals("null", ignoreCase = true) }
            ?.takeIf { candidate -> existing.any { it.id == candidate } }
        return ProfileMatch(matched, reason)
    }

    /** 空清单直接判定为新建（不调用 LLM）。 */
    suspend fun match(targetRole: String, existing: List<MemoryProfileEntry>): ProfileMatch {
        if (existing.isEmpty()) return ProfileMatch(null)
        val raw = aiClient.chat(buildMessages(targetRole, existing))
        return parse(raw, existing)
    }
}

/**
 * 方向匹配结果。
 * @param matchedId 命中的已有记忆集 id；为 null 表示应新建一份。
 */
data class ProfileMatch(val matchedId: String?, val reason: String = "")
