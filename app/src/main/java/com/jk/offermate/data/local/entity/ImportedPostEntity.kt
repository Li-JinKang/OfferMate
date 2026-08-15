package com.jk.offermate.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 已导入帖子的持久化实体（P3 最小版，用于验证 Room/KSP；后续补全字段）。
 */
@Entity(tableName = "imported_post")
data class ImportedPostEntity(
    @PrimaryKey val id: String,
    val platform: String,
    val url: String,
    val title: String,
    val status: String,
    val importedAt: Long
)
