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

    /**
     * 是否有待执行的 AI 分析任务。
     * 导入/保存简历文本时置 true；Worker 成功完成后置 false。
     * 用于在用户补配置 API Key 时重新触发分析。
     */
    val needsAiAnalysis: Flow<Boolean>

    suspend fun save(targetRole: String, skillsCsv: String, rawText: String)

    /** 仅更新简历文本（识别出的内容），保留其他字段。 */
    suspend fun updateRawText(rawText: String)

    /** 记录/清除简历文件路径。 */
    suspend fun setFilePath(path: String?)

    /** 更新 AI 分析待处理标记。 */
    suspend fun setNeedsAiAnalysis(needs: Boolean)

    companion object {
        /** 把用户输入的技能串（逗号/顿号/换行分隔）解析为列表。 */
        fun parseSkills(csv: String): List<String> =
            csv.split(',', '，', '、', ';', '；', '\n')
                .map { it.trim() }
                .filter { it.isNotEmpty() }
    }
}
