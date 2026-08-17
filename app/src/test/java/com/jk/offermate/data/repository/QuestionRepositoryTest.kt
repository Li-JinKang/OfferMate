package com.jk.offermate.data.repository

import com.jk.offermate.agent.Difficulty
import com.jk.offermate.agent.QuestionSource
import com.jk.offermate.data.local.dao.FingerprintRow
import com.jk.offermate.data.local.dao.QuestionDao
import com.jk.offermate.data.local.entity.QuestionEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QuestionRepositoryTest {

    private class FakeQuestionDao : QuestionDao {
        val all = MutableStateFlow<List<QuestionEntity>>(emptyList())
        override fun observeByPost(postId: String): Flow<List<QuestionEntity>> = all.map { l -> l.filter { it.postId == postId } }
        override fun observeAll(): Flow<List<QuestionEntity>> = all
        override fun observeById(id: String): Flow<QuestionEntity?> = all.map { l -> l.firstOrNull { it.id == id } }
        override suspend fun setPracticed(id: String, practiced: Boolean) {
            all.value = all.value.map { if (it.id == id) it.copy(practiced = practiced) else it }
        }
        override suspend fun updateAnswer(id: String, answer: String) {
            all.value = all.value.map { if (it.id == id) it.copy(answer = answer) else it }
        }
        override suspend fun countByPost(postId: String): Int = all.value.count { it.postId == postId }
        override suspend fun fingerprintsInBuckets(buckets: List<String>): List<FingerprintRow> =
            all.value.filter { it.bucketKey in buckets }.map { FingerprintRow(it.exactHash, it.simhash, it.bucketKey) }
        override suspend fun existingExactHashes(hashes: List<String>): List<String> =
            all.value.map { it.exactHash }.filter { it in hashes }
        override suspend fun insertAll(questions: List<QuestionEntity>) { all.value = all.value + questions }
        override suspend fun insert(question: QuestionEntity) { all.value = all.value + question }
        override suspend fun deleteById(id: String) { all.value = all.value.filterNot { it.id == id } }
        override suspend fun deleteByPost(postId: String) { all.value = all.value.filterNot { it.postId == postId } }
    }

    private val dao = FakeQuestionDao()
    private val repo = RoomQuestionRepository(dao)

    @Test
    fun `addManualQuestion stores as MANUAL under its category`() = runTest {
        repo.addManualQuestion("什么是协程", "答案", "Kotlin", Difficulty.HARD)

        val all = repo.observeAll().first()
        assertEquals(1, all.size)
        val q = all.first()
        assertEquals("什么是协程", q.question)
        assertEquals(QuestionSource.MANUAL, q.source)
        assertEquals(listOf("Kotlin"), q.tags)
        assertEquals(Difficulty.HARD, q.difficulty)
        assertTrue(q.id.startsWith("manual_"))
    }

    @Test
    fun `blank question is not added`() = runTest {
        repo.addManualQuestion("   ", "a", "Kotlin", Difficulty.EASY)
        assertTrue(repo.observeAll().first().isEmpty())
    }

    @Test
    fun `deleteQuestion removes it`() = runTest {
        repo.addManualQuestion("题目A", "", "网络", Difficulty.MEDIUM)
        val id = repo.observeAll().first().first().id
        repo.deleteQuestion(id)
        assertTrue(repo.observeAll().first().isEmpty())
    }
}
