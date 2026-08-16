package com.jk.offermate.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 分析产出的题目（关联到某个 ImportedPost）。
 * 多值字段用换行连接存储，读取时按行拆分。
 */
@Entity(
    tableName = "question",
    indices = [Index("postId"), Index("bucketKey"), Index("exactHash")]
)
data class QuestionEntity(
    @PrimaryKey val id: String,
    val postId: String,
    val orderIndex: Int,
    val question: String,
    val answer: String,
    val tagsCsv: String,
    val difficulty: String,
    val keyPointsCsv: String,
    val relevanceScore: Int,
    val relevanceReason: String,
    val practiced: Boolean = false,
    /** 去重指纹（规范化文本，精确重复判定）。 */
    val exactHash: String = "",
    /** 去重指纹（64 位 SimHash，近似重复判定）。 */
    val simhash: Long = 0L,
    /** LSH 分桶键（当前按首个考点标签分区）。 */
    val bucketKey: String = ""
)
