package com.jk.offermate.data.resume

import com.jk.offermate.data.ai.ResumeProfile
import kotlinx.coroutines.flow.Flow

/**
 * 简历画像仓库。MVP 用手动录入（目标岗位 + 技能 + 简历文本）。
 */
interface ResumeRepository {
    val profile: Flow<ResumeProfile>

    suspend fun save(targetRole: String, skillsCsv: String, rawText: String)

    companion object {
        /** 把用户输入的技能串（逗号/顿号/换行分隔）解析为列表。 */
        fun parseSkills(csv: String): List<String> =
            csv.split(',', '，', '、', ';', '；', '\n')
                .map { it.trim() }
                .filter { it.isNotEmpty() }
    }
}
