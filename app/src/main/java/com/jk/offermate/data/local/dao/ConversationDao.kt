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

    @Query("SELECT * FROM conversation WHERE questionId = :questionId ORDER BY updatedAt DESC LIMIT 1")
    suspend fun findLatestByQuestionId(questionId: String): ConversationEntity?

    /** 每道题的会话概要：会话数量 + 最近活跃时间（用于 AI 对话页“继续对话”与已聊标记）。 */
    @Query(
        "SELECT questionId AS questionId, COUNT(*) AS count, MAX(updatedAt) AS lastUpdated " +
            "FROM conversation WHERE questionId IS NOT NULL GROUP BY questionId"
    )
    fun observeConversationSummaries(): Flow<List<ConversationSummaryRow>>

    @Query("SELECT * FROM conversation WHERE questionId = :questionId ORDER BY createdAt ASC")
    fun observeAllByQuestionId(questionId: String): Flow<List<ConversationEntity>>

    /** 全部会话（按最近活跃时间倒序），用于 AI 对话页抽屉的历史列表。 */
    @Query("SELECT * FROM conversation ORDER BY updatedAt DESC")
    fun observeAllConversations(): Flow<List<ConversationEntity>>

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

/** [ConversationDao.observeConversationSummaries] 的投影行。 */
data class ConversationSummaryRow(
    val questionId: String,
    val count: Int,
    val lastUpdated: Long
)
