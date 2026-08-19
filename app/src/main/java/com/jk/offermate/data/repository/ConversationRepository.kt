package com.jk.offermate.data.repository

import com.jk.offermate.agent.ChatMessage
import com.jk.offermate.agent.Role
import com.jk.offermate.data.local.buildSnippet
import com.jk.offermate.data.local.dao.ConversationDao
import com.jk.offermate.data.local.entity.ChatMessageEntity
import com.jk.offermate.data.local.entity.ConversationEntity
import com.jk.offermate.data.local.toLikePattern
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.util.UUID

/** 某道题的会话概要：会话数量与最近活跃时间。 */
data class ConversationInfo(val count: Int, val lastUpdated: Long)

/**
 * 会话搜索命中项：会话基本信息 + 命中消息片段与其 id。
 * [snippet]/[hitMessageId] 为空表示仅标题命中（无消息内容命中）。
 */
data class ConversationSearchHit(
    val conversationId: String,
    val questionId: String?,
    val title: String,
    val updatedAt: Long,
    val snippet: String?,
    val hitMessageId: Long?
)

/**
 * 会话仓库：会话 CRUD、追加消息、加载上下文。全部本地持久化。
 */
interface ConversationRepository {
    /** 取得某道题最近一次会话；不存在则新建一个，返回会话 id。 */
    suspend fun getOrCreateForQuestion(questionId: String, title: String): String

    /** 为某道题另起一轮新会话（不复用已有会话），返回新会话 id。 */
    suspend fun createNewForQuestion(questionId: String, title: String): String

    /** 新建一个自由对话会话（不绑定题目），返回新会话 id。 */
    suspend fun createNewChat(title: String): String

    /** 观察某道题下的所有会话（按创建时间升序，用于会话切换器 UI）。 */
    fun observeConversationsForQuestion(questionId: String): Flow<List<ConversationEntity>>

    /** 观察每道题的会话概要（questionId -> 概要），用于 AI 对话页列表。 */
    fun observeConversationSummaries(): Flow<Map<String, ConversationInfo>>

    /** 观察全部会话（按最近活跃时间倒序），用于 AI 对话页抽屉的历史列表。 */
    fun observeAllConversations(): Flow<List<ConversationEntity>>

    /** 搜索会话：标题或消息内容命中（DB LIKE，结果有上限），带出命中片段与消息 id。 */
    fun searchConversations(query: String, limit: Int = 50): Flow<List<ConversationSearchHit>>

    /** 命中消息在会话消息列表中的下标（用于打开会话后滚动定位）；找不到返回 -1。 */
    suspend fun messageIndex(conversationId: String, messageId: Long): Int

    /** 观察某会话的消息（用于 UI）。 */
    fun observeMessages(conversationId: String): Flow<List<ChatMessage>>

    /** 快照读取会话历史（用于组装发送给模型的上下文）。 */
    suspend fun history(conversationId: String): List<ChatMessage>

    /** 追加一条消息。 */
    suspend fun append(conversationId: String, role: Role, content: String)

    /** 清空并删除会话。 */
    suspend fun clear(conversationId: String)
}

class RoomConversationRepository(
    private val dao: ConversationDao,
    private val now: () -> Long = { System.currentTimeMillis() }
) : ConversationRepository {

    override suspend fun getOrCreateForQuestion(questionId: String, title: String): String {
        dao.findLatestByQuestionId(questionId)?.let { return it.id }
        return createNewForQuestion(questionId, title)
    }

    override suspend fun createNewForQuestion(questionId: String, title: String): String {
        val id = UUID.randomUUID().toString()
        val ts = now()
        dao.insert(
            ConversationEntity(
                id = id,
                questionId = questionId,
                title = title,
                createdAt = ts,
                updatedAt = ts
            )
        )
        return id
    }

    override suspend fun createNewChat(title: String): String {
        val id = UUID.randomUUID().toString()
        val ts = now()
        dao.insert(
            ConversationEntity(
                id = id,
                questionId = null,
                title = title,
                createdAt = ts,
                updatedAt = ts
            )
        )
        return id
    }

    override fun observeConversationsForQuestion(questionId: String): Flow<List<ConversationEntity>> =
        dao.observeAllByQuestionId(questionId)

    override fun observeConversationSummaries(): Flow<Map<String, ConversationInfo>> =
        dao.observeConversationSummaries().map { rows ->
            rows.associate { it.questionId to ConversationInfo(it.count, it.lastUpdated) }
        }

    override fun observeAllConversations(): Flow<List<ConversationEntity>> =
        dao.observeAllConversations()

    override fun searchConversations(query: String, limit: Int): Flow<List<ConversationSearchHit>> {
        val kw = query.trim()
        if (kw.isEmpty()) return flowOf(emptyList())
        return dao.search(toLikePattern(kw), limit).map { rows ->
            rows.map { r ->
                ConversationSearchHit(
                    conversationId = r.id,
                    questionId = r.questionId,
                    title = r.title,
                    updatedAt = r.updatedAt,
                    // 只保留关键词附近一小段，控制堆占用与展示长度
                    snippet = r.snippet?.let { buildSnippet(it, kw) },
                    hitMessageId = r.hitMessageId
                )
            }
        }
    }

    override fun observeMessages(conversationId: String): Flow<List<ChatMessage>> =
        dao.observeMessages(conversationId).map { list -> list.map(::toDomain) }

    override suspend fun history(conversationId: String): List<ChatMessage> =
        dao.messagesOf(conversationId).map(::toDomain)

    override suspend fun messageIndex(conversationId: String, messageId: Long): Int =
        dao.messagePositionOf(conversationId, messageId) - 1

    override suspend fun append(conversationId: String, role: Role, content: String) {
        dao.insertMessage(
            ChatMessageEntity(
                conversationId = conversationId,
                role = role.name,
                content = content,
                createdAt = now()
            )
        )
        dao.touch(conversationId, now())
    }

    override suspend fun clear(conversationId: String) {
        dao.deleteMessages(conversationId)
        dao.delete(conversationId)
    }

    private fun toDomain(e: ChatMessageEntity): ChatMessage =
        ChatMessage(role = parseRole(e.role), content = e.content)

    private fun parseRole(name: String): Role =
        runCatching { Role.valueOf(name) }.getOrDefault(Role.USER)
}
