package com.jk.offermate.data.repository

import com.jk.offermate.data.ai.AnsweredQuestion
import com.jk.offermate.data.local.PostMappers
import com.jk.offermate.data.local.dao.QuestionDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

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
}
