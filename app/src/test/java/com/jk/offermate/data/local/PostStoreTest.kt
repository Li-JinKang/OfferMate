package com.jk.offermate.data.local

import com.jk.offermate.data.ai.AnsweredQuestion
import com.jk.offermate.data.local.dao.ImportedPostDao
import com.jk.offermate.data.local.dao.QuestionDao
import com.jk.offermate.data.local.entity.ImportedPostEntity
import com.jk.offermate.data.local.entity.QuestionEntity
import com.jk.offermate.domain.model.ImportStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PostStoreTest {

    private class FakeImportedPostDao : ImportedPostDao {
        val posts = MutableStateFlow<Map<String, ImportedPostEntity>>(emptyMap())
        override fun observeAll(): Flow<List<ImportedPostEntity>> = posts.map { it.values.toList() }
        override fun observeById(id: String): Flow<ImportedPostEntity?> = posts.map { it[id] }
        override suspend fun findById(id: String): ImportedPostEntity? = posts.value[id]
        override suspend fun upsert(post: ImportedPostEntity) { posts.value = posts.value + (post.id to post) }
        override suspend fun updateStatus(id: String, status: String, updatedAt: Long) {
            posts.value[id]?.let { posts.value = posts.value + (id to it.copy(status = status, updatedAt = updatedAt)) }
        }
        override suspend fun setPinned(id: String, pinned: Boolean, updatedAt: Long) {
            posts.value[id]?.let { posts.value = posts.value + (id to it.copy(pinned = pinned, updatedAt = updatedAt)) }
        }
        override suspend fun delete(id: String) { posts.value = posts.value - id }
        override suspend fun clear() { posts.value = emptyMap() }
    }

    private class FakeQuestionDao : QuestionDao {
        val byPost = MutableStateFlow<Map<String, List<QuestionEntity>>>(emptyMap())
        override fun observeByPost(postId: String): Flow<List<QuestionEntity>> = byPost.map { it[postId] ?: emptyList() }
        override fun observeAll(): Flow<List<QuestionEntity>> = byPost.map { it.values.flatten() }
        override suspend fun countByPost(postId: String): Int = byPost.value[postId]?.size ?: 0
        override suspend fun insertAll(questions: List<QuestionEntity>) {
            val pid = questions.firstOrNull()?.postId ?: return
            byPost.value = byPost.value + (pid to ((byPost.value[pid] ?: emptyList()) + questions))
        }
        override suspend fun deleteByPost(postId: String) { byPost.value = byPost.value - postId }
    }

    private val postDao = FakeImportedPostDao()
    private val questionDao = FakeQuestionDao()
    private val store = PostStore(postDao, questionDao, now = { 1000L })

    @Test
    fun `createPending inserts pending record with detected platform`() = runTest {
        store.createPending("p1", "https://www.nowcoder.com/x")

        val e = postDao.findById("p1")!!
        assertEquals(ImportStatus.PENDING.name, e.status)
        assertEquals("NOWCODER", e.platform)
        assertEquals(0, e.questionCount)
    }

    @Test
    fun `saveSuccess stores questions and marks done`() = runTest {
        store.createPending("p1", "https://www.nowcoder.com/x")
        val questions = listOf(
            AnsweredQuestion(question = "Q1", answer = "A1", relevanceScore = 90),
            AnsweredQuestion(question = "Q2", answer = "A2", relevanceScore = 70)
        )

        store.saveSuccess("p1", title = "字节面经", summary = "摘要", questions = questions)

        val e = postDao.findById("p1")!!
        assertEquals(ImportStatus.DONE.name, e.status)
        assertEquals(2, e.questionCount)
        assertEquals("字节面经", e.title)
        assertEquals(2, questionDao.countByPost("p1"))
    }

    @Test
    fun `markFailed and markNeedsManual update status`() = runTest {
        store.createPending("p1", "https://xhslink.cn/x")
        store.markNeedsManual("p1")
        assertEquals(ImportStatus.NEEDS_MANUAL_INPUT.name, postDao.findById("p1")!!.status)

        store.markFailed("p1")
        assertEquals(ImportStatus.FAILED.name, postDao.findById("p1")!!.status)
    }

    @Test
    fun `delete removes post and its questions`() = runTest {
        store.createPending("p1", "https://www.nowcoder.com/x")
        store.saveSuccess("p1", "t", "s", listOf(AnsweredQuestion(question = "Q1", answer = "A1")))

        store.delete("p1")

        assertNull(postDao.findById("p1"))
        assertEquals(0, questionDao.countByPost("p1"))
    }
}
