package com.jk.offermate.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 一次会话。当前用于"题目追问"：每道题对应一个会话（[questionId] 唯一）。
 */
@Entity(
    tableName = "conversation",
    indices = [Index(value = ["questionId"], unique = true)]
)
data class ConversationEntity(
    @PrimaryKey val id: String,
    val questionId: String?,
    val title: String,
    val createdAt: Long,
    val updatedAt: Long
)
