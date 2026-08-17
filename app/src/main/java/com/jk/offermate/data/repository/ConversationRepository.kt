package com.jk.offermate.data.repository

import com.jk.offermate.agent.ChatMessage
import com.jk.offermate.agent.Role
import com.jk.offermate.data.local.dao.ConversationDao
import com.jk.offermate.data.local.entity.ChatMessageEntity
import com.jk.offermate.data.local.entity.ConversationEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

/**
 * 会话仓库：会话 CRUD、追加消息、加载上下文。全部本地持久化。
 */
interface ConversationRepository {
    /** 取得（或创建）某道题对应的会话，返回会话 id。 */
    suspend fun getOrCreateForQuestion(questionId: String, title: String): String

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
        dao.findByQuestionId(questionId)?.let { return it.id }
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

    override fun observeMessages(conversationId: String): Flow<List<ChatMessage>> =
        dao.observeMessages(conversationId).map { list -> list.map(::toDomain) }

    override suspend fun history(conversationId: String): List<ChatMessage> =
        dao.messagesOf(conversationId).map(::toDomain)

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
