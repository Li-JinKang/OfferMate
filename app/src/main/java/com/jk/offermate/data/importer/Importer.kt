package com.jk.offermate.data.importer

import com.jk.offermate.agent.ResumeProfile

/**
 * 导入用例抽象（供 UI 依赖，便于用 fake 做 ViewModel 单测）。
 */
interface Importer {
    suspend fun importFromUrl(url: String, profile: ResumeProfile): ImportResult
    suspend fun importFromText(text: String, profile: ResumeProfile, sourceUrl: String = ""): ImportResult
}
