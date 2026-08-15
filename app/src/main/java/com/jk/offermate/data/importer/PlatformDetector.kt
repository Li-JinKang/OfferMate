package com.jk.offermate.data.importer

import com.jk.offermate.domain.model.Platform

/** 根据链接识别来源平台（纯函数）。 */
object PlatformDetector {
    fun detect(url: String): Platform {
        val u = url.lowercase()
        return when {
            "xiaohongshu" in u || "xhslink" in u || "xhs" in u -> Platform.XIAOHONGSHU
            "nowcoder" in u -> Platform.NOWCODER
            else -> Platform.NOWCODER
        }
    }
}
