package com.jk.offermate.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 已导入帖子的持久化实体。
 *
 * status 取值对应导入任务状态机（见 docs/plan/ui-and-runtime.md）：
 * PENDING / READING / NEEDS_MANUAL_INPUT / ANALYZING / DONE / READ_FAILED / ANALYZE_FAILED
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
    val importedAt: Long,
    val updatedAt: Long
)
