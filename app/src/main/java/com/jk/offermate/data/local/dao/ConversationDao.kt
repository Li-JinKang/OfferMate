package com.jk.offermate.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.jk.offermate.data.local.entity.ChatMessageEntity
import com.jk.offermate.data.local.entity.ConversationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ConversationDao {

    @Query("SELECT * FROM conversation WHERE questionId = :questionId LIMIT 1")
    suspend fun findByQuestionId(questionId: String): ConversationEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(conversation: ConversationEntity)

    @Query("UPDATE conversation SET updatedAt = :updatedAt WHERE id = :id")
    suspend fun touch(id: String, updatedAt: Long)

    @Query("DELETE FROM conversation WHERE id = :id")
    suspend fun delete(id: String)

    @Query("SELECT * FROM chat_message WHERE conversationId = :conversationId ORDER BY id")
    fun observeMessages(conversationId: String): Flow<List<ChatMessageEntity>>

    @Query("SELECT * FROM chat_message WHERE conversationId = :conversationId ORDER BY id")
    suspend fun messagesOf(conversationId: String): List<ChatMessageEntity>

    @Insert
    suspend fun insertMessage(message: ChatMessageEntity)

    @Query("DELETE FROM chat_message WHERE conversationId = :conversationId")
    suspend fun deleteMessages(conversationId: String)
}
