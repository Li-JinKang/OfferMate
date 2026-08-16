package com.jk.offermate.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 会话中的一条消息（role ∈ system/user/assistant）。
 */
@Entity(
    tableName = "chat_message",
    indices = [Index("conversationId")]
)
data class ChatMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val conversationId: String,
    val role: String,
    val content: String,
    val createdAt: Long
)
