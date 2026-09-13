package com.jk.offermate.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 已导入帖子的持久化实体。status 取值见 [com.jk.offermate.domain.model.ImportStatus]。
 */
@Entity(tableName = "imported_post")
data class ImportedPostEntity(
    @PrimaryKey val id: String,
    val platform: String,
    val url: String,
    val resolvedUrl: String?,
    val title: String,
    val summary: String,
    val status: String,
    val questionCount: Int,
    val importedAt: Long,
    val updatedAt: Long,
    val pinned: Boolean = false,
    /**
     * 终态失败的原因，直接透到首页卡片。
     * 原先只存 status=FAILED，用户只能看到"失败"两个字，排查全靠 Logcat。
     */
    val failureReason: String? = null
)
