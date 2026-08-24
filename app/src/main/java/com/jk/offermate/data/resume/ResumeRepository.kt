package com.jk.offermate.data.resume

import com.jk.offermate.agent.resume.ResumeProfile
import kotlinx.coroutines.flow.Flow

/**
 * 简历画像仓库。MVP 用手动录入（目标岗位 + 技能 + 简历文本）。
 */
interface ResumeRepository {
    val profile: Flow<ResumeProfile>

    /** 已保存的简历文件本地路径（用于预览渲染）；未导入时为空。 */
    val resumeFilePath: Flow<String?>

    suspend fun save(targetRole: String, skillsCsv: String, rawText: String)

    /** 仅更新简历文本（识别出的内容），保留其他字段。 */
    suspend fun updateRawText(rawText: String)

    /** 记录/清除简历文件路径。 */
    suspend fun setFilePath(path: String?)

    companion object {
        /** 把用户输入的技能串（逗号/顿号/换行分隔）解析为列表。 */
        fun parseSkills(csv: String): List<String> =
            csv.split(',', '，', '、', ';', '；', '\n')
                .map { it.trim() }
                .filter { it.isNotEmpty() }
    }
}
