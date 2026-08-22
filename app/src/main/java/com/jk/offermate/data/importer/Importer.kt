package com.jk.offermate.data.importer

/**
 * 导入用例抽象（供 UI 依赖，便于用 fake 做 ViewModel 单测）。
 * 候选人背景由分析流水线按需经记忆工具拉取，不再由调用方传入。
 */
interface Importer {
    suspend fun importFromUrl(url: String): ImportResult
    suspend fun importFromText(text: String, sourceUrl: String = ""): ImportResult
}
