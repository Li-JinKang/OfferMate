package com.jk.offermate.agent

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * 简历结构化：把简历原文交给 LLM，解析成 [StructuredResume]（岗位、技能、项目、经历、共享事实）。
 *
 * 纯逻辑的 [buildMessages]/[parse] 可用 [FakeAiClient] 单测；[structure] 做一次 LLM 调用。
 */
class ResumeStructurer(private val aiClient: AiClient) {

    fun buildMessages(rawText: String): List<ChatMessage> = listOf(
        ChatMessage(
            role = Role.SYSTEM,
            content = """
                你是简历结构化助手。用户会给你一份简历原文，请抽取为结构化 JSON。
                严格要求：
                1. 仅输出 JSON，不要任何解释或额外文字。
                2. 结构：
                {
                  "id": "英文小写短横线 slug，概括求职方向，如 java-backend / android",
                  "name": "方向的中文展示名，如 Java 后端",
                  "targetRole": "目标岗位，如 Java 后端开发工程师",
                  "summary": "一句话概括候选人（含年限与核心技能）",
                  "skills": ["技能1", "技能2"],
                  "globalFacts": ["工作年限: 3 年", "学历: 本科", "语言: 中/英"],
                  "projects": [{"id":"英文slug","title":"项目名","brief":"一句话简介","detail":"背景/职责/技术栈/难点/亮点，多行"}],
                  "experiences": [{"id":"英文slug","title":"公司-角色","brief":"一句话简介","detail":"职责与成果，多行"}]
                }
                3. globalFacts 只放与求职方向无关的通用事实（年限、学历、语言）。
                4. 无法识别的字段用空数组或空字符串；不要臆造简历中不存在的内容。
            """.trimIndent()
        ),
        ChatMessage(role = Role.USER, content = "简历原文如下：\n\n$rawText")
    )

    suspend fun structure(rawText: String): StructuredResume {
        val raw = aiClient.chat(buildMessages(rawText))
        return parse(raw)
    }

    /** 解析模型输出为 [StructuredResume]。容错：兼容 ```json``` 包裹与前后多余文字。 */
    fun parse(raw: String): StructuredResume {
        val jsonText = JsonSupport.extractJsonBlock(raw)
            ?: throw AiException("模型输出中未找到有效 JSON：${raw.take(200)}")
        val obj = runCatching { JsonSupport.json.parseToJsonElement(jsonText) }
            .getOrNull() as? JsonObject
            ?: throw AiException("简历结构化 JSON 解析失败：${jsonText.take(200)}")

        val targetRole = obj.str("targetRole")
        if (targetRole.isEmpty() && obj.str("name").isEmpty()) {
            throw AiException("简历结构化结果缺少 targetRole/name")
        }

        return StructuredResume(
            suggestedId = obj.str("id"),
            name = obj.str("name").ifEmpty { targetRole },
            targetRole = targetRole.ifEmpty { obj.str("name") },
            summary = obj.str("summary"),
            skills = obj.strList("skills"),
            globalFacts = obj.strList("globalFacts"),
            projects = obj.detailList("projects"),
            experiences = obj.detailList("experiences")
        )
    }

    private fun JsonObject.str(key: String): String =
        this[key]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()

    private fun JsonObject.strList(key: String): List<String> =
        (this[key] as? JsonArray)
            ?.mapNotNull { it.jsonPrimitive.contentOrNull?.trim()?.takeIf(String::isNotEmpty) }
            ?: emptyList()

    private fun JsonObject.detailList(key: String): List<ResumeDetail> =
        (this[key] as? JsonArray)?.mapNotNull { el ->
            val o = el as? JsonObject ?: return@mapNotNull null
            val title = o.str("title")
            val detail = o.str("detail")
            if (title.isEmpty() && detail.isEmpty()) return@mapNotNull null
            ResumeDetail(
                id = o.str("id"),
                title = title,
                brief = o.str("brief"),
                detail = detail
            )
        } ?: emptyList()
}
