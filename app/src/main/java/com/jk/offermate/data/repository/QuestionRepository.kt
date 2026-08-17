package com.jk.offermate.data.repository

import com.jk.offermate.data.ai.AnsweredQuestion
import com.jk.offermate.data.ai.Difficulty
import com.jk.offermate.data.ai.QuestionSource
import com.jk.offermate.data.local.PostMappers
import com.jk.offermate.data.local.dao.QuestionDao
import com.jk.offermate.data.local.entity.QuestionEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

/** 题目查询仓库。 */
interface QuestionRepository {
    /** 某帖子的题目。 */
    fun observeByPost(postId: String): Flow<List<AnsweredQuestion>>

    /** 跨所有帖子的题目（题库）。 */
    fun observeAll(): Flow<List<AnsweredQuestion>>

    /** 观察单道题（追问页用，答案更新后自动反映）。 */
    fun observeById(questionId: String): Flow<AnsweredQuestion?>

    /** 标记某题已刷/未刷。 */
    suspend fun setPracticed(questionId: String, practiced: Boolean)

    /** 更新某题的答案（追问后据讨论重写答案）。 */
    suspend fun updateAnswer(questionId: String, answer: String)

    /** 用户手动新增一道题（归入 [category] 分类）。 */
    suspend fun addManualQuestion(
        question: String,
        answer: String,
        category: String,
        difficulty: Difficulty = Difficulty.UNKNOWN
    )

    /** 删除一道题（用于删除手动添加的题目）。 */
    suspend fun deleteQuestion(questionId: String)

    companion object {
        /** 手动题目所属的虚拟帖子 id。 */
        const val MANUAL_POST_ID = "manual"
    }
}

class RoomQuestionRepository(private val questionDao: QuestionDao) : QuestionRepository {
    override fun observeByPost(postId: String): Flow<List<AnsweredQuestion>> =
        questionDao.observeByPost(postId).map { list -> list.map(PostMappers::toAnswered) }

    override fun observeAll(): Flow<List<AnsweredQuestion>> =
        questionDao.observeAll().map { list -> list.map(PostMappers::toAnswered) }

    override fun observeById(questionId: String): Flow<AnsweredQuestion?> =
        questionDao.observeById(questionId).map { it?.let(PostMappers::toAnswered) }

    override suspend fun setPracticed(questionId: String, practiced: Boolean) {
        questionDao.setPracticed(questionId, practiced)
    }

    override suspend fun updateAnswer(questionId: String, answer: String) {
        questionDao.updateAnswer(questionId, answer)
    }

    override suspend fun addManualQuestion(
        question: String,
        answer: String,
        category: String,
        difficulty: Difficulty
    ) {
        val q = question.trim()
        if (q.isEmpty()) return
        val tag = category.trim()
        questionDao.insert(
            QuestionEntity(
                id = "${QuestionRepository.MANUAL_POST_ID}_${UUID.randomUUID()}",
                postId = QuestionRepository.MANUAL_POST_ID,
                orderIndex = 0,
                question = q,
                answer = answer.trim(),
                tagsCsv = tag,
                difficulty = difficulty.name,
                keyPointsCsv = "",
                relevanceScore = 100, // 手动题默认置顶
                relevanceReason = "手动添加",
                source = QuestionSource.MANUAL.name
            )
        )
    }

    override suspend fun deleteQuestion(questionId: String) {
        if (questionId.isBlank()) return
        questionDao.deleteById(questionId)
    }
}
