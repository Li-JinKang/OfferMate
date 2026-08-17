package com.jk.offermate.data.repository

import com.jk.offermate.agent.Role
import com.jk.offermate.data.local.dao.ConversationDao
import com.jk.offermate.data.local.entity.ChatMessageEntity
import com.jk.offermate.data.local.entity.ConversationEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversationRepositoryTest {

    private class FakeConversationDao : ConversationDao {
        val conversations = MutableStateFlow<Map<String, ConversationEntity>>(emptyMap())
        val messages = MutableStateFlow<List<ChatMessageEntity>>(emptyList())
        private var autoId = 1L

        override suspend fun findByQuestionId(questionId: String): ConversationEntity? =
            conversations.value.values.firstOrNull { it.questionId == questionId }

        override suspend fun insert(conversation: ConversationEntity) {
            conversations.value = conversations.value + (conversation.id to conversation)
        }

        override suspend fun touch(id: String, updatedAt: Long) {
            conversations.value[id]?.let {
                conversations.value = conversations.value + (id to it.copy(updatedAt = updatedAt))
            }
        }

        override suspend fun delete(id: String) {
            conversations.value = conversations.value - id
        }

        override fun observeMessages(conversationId: String): Flow<List<ChatMessageEntity>> =
            messages.map { list -> list.filter { it.conversationId == conversationId } }

        override suspend fun messagesOf(conversationId: String): List<ChatMessageEntity> =
            messages.value.filter { it.conversationId == conversationId }

        override suspend fun insertMessage(message: ChatMessageEntity) {
            messages.value = messages.value + message.copy(id = autoId++)
        }

        override suspend fun deleteMessages(conversationId: String) {
            messages.value = messages.value.filterNot { it.conversationId == conversationId }
        }
    }

    private val dao = FakeConversationDao()
    private var clock = 100L
    private val repo = RoomConversationRepository(dao, now = { clock })

    @Test
    fun `getOrCreateForQuestion creates once and reuses`() = runTest {
        val id1 = repo.getOrCreateForQuestion("q1", "标题")
        val id2 = repo.getOrCreateForQuestion("q1", "标题")
        assertEquals(id1, id2)

        val other = repo.getOrCreateForQuestion("q2", "别的题")
        assertNotEquals(id1, other)
    }

    @Test
    fun `append persists messages in order and observable`() = runTest {
        val id = repo.getOrCreateForQuestion("q1", "标题")
        repo.append(id, Role.USER, "第一问")
        repo.append(id, Role.ASSISTANT, "第一答")
        repo.append(id, Role.USER, "追问")

        val history = repo.history(id)
        assertEquals(listOf("第一问", "第一答", "追问"), history.map { it.content })
        assertEquals(listOf(Role.USER, Role.ASSISTANT, Role.USER), history.map { it.role })

        val observed = repo.observeMessages(id).first()
        assertEquals(3, observed.size)
    }

    @Test
    fun `append updates conversation timestamp`() = runTest {
        val id = repo.getOrCreateForQuestion("q1", "标题")
        clock = 500L
        repo.append(id, Role.USER, "hi")
        assertEquals(500L, dao.conversations.value[id]!!.updatedAt)
    }

    @Test
    fun `clear removes conversation and messages`() = runTest {
        val id = repo.getOrCreateForQuestion("q1", "标题")
        repo.append(id, Role.USER, "hi")

        repo.clear(id)

        assertNull(dao.findByQuestionId("q1"))
        assertTrue(repo.history(id).isEmpty())
    }
}
