package com.jk.offermate.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 一次会话。用于"题目追问"：一道题可以有多轮独立会话（[questionId] 非唯一），
 * 便于用户就同一道题从不同角度分别展开讨论，互不干扰历史上下文。
 */
@Entity(
    tableName = "conversation",
    indices = [Index(value = ["questionId"])]
)
data class ConversationEntity(
    @PrimaryKey val id: String,
    val questionId: String?,
    val title: String,
    val createdAt: Long,
    val updatedAt: Long,
    /** 是否置顶：置顶会话在历史列表中恒排在最前。 */
    val pinned: Boolean = false
)
