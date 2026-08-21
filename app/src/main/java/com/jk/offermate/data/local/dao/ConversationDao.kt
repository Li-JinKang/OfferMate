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

    /** 全部会话（置顶优先，其余按最近活跃时间倒序），用于 AI 对话页抽屉的历史列表。 */
    @Query("SELECT * FROM conversation ORDER BY pinned DESC, updatedAt DESC")
    fun observeAllConversations(): Flow<List<ConversationEntity>>

    /** 观察单个会话（用于顶部标题实时反映摘要标题）。 */
    @Query("SELECT * FROM conversation WHERE id = :id")
    fun observeById(id: String): Flow<ConversationEntity?>

    /** 更新会话标题（首轮对话后生成摘要标题时调用一次；也用于重命名）。 */
    @Query("UPDATE conversation SET title = :title WHERE id = :id")
    suspend fun updateTitle(id: String, title: String)

    /** 设置会话置顶状态。 */
    @Query("UPDATE conversation SET pinned = :pinned WHERE id = :id")
    suspend fun setPinned(id: String, pinned: Boolean)

    /**
     * 按会话标题或消息内容搜索会话（按最近活跃倒序）。
     * 命中消息时带出最近一条命中消息的正文([snippet])与其 id([hitMessageId])，用于展示片段与跳转定位；
     * 仅标题命中时二者为 null。[kw] 需已包 `%关键词%` 并转义（配合 ESCAPE '\'）。
     */
    @Query(
        "SELECT c.id AS id, c.questionId AS questionId, c.title AS title, c.updatedAt AS updatedAt, c.pinned AS pinned, " +
            "(SELECT m.content FROM chat_message m WHERE m.conversationId = c.id AND m.content LIKE :kw ESCAPE '\\' ORDER BY m.id DESC LIMIT 1) AS snippet, " +
            "(SELECT m.id FROM chat_message m WHERE m.conversationId = c.id AND m.content LIKE :kw ESCAPE '\\' ORDER BY m.id DESC LIMIT 1) AS hitMessageId " +
            "FROM conversation c " +
            "WHERE c.title LIKE :kw ESCAPE '\\' " +
            "OR EXISTS (SELECT 1 FROM chat_message m WHERE m.conversationId = c.id AND m.content LIKE :kw ESCAPE '\\') " +
            "ORDER BY c.pinned DESC, c.updatedAt DESC LIMIT :limit"
    )
    fun search(kw: String, limit: Int): Flow<List<ConversationSearchRow>>

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

    /** 命中消息在其会话消息列表（按 id 升序）中的 1-based 序号；下标 = 结果 - 1。用于搜索跳转定位。 */
    @Query("SELECT COUNT(*) FROM chat_message WHERE conversationId = :conversationId AND id <= :messageId")
    suspend fun messagePositionOf(conversationId: String, messageId: Long): Int

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

/** [ConversationDao.search] 的投影行：会话基本信息 + 命中消息片段与其 id（仅标题命中时后两者为 null）。 */
data class ConversationSearchRow(
    val id: String,
    val questionId: String?,
    val title: String,
    val updatedAt: Long,
    val pinned: Boolean,
    val snippet: String?,
    val hitMessageId: Long?
)
