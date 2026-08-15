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
}

class RoomQuestionRepository(private val questionDao: QuestionDao) : QuestionRepository {
    override fun observeByPost(postId: String): Flow<List<AnsweredQuestion>> =
        questionDao.observeByPost(postId).map { list -> list.map(PostMappers::toAnswered) }

    override fun observeAll(): Flow<List<AnsweredQuestion>> =
        questionDao.observeAll().map { list -> list.map(PostMappers::toAnswered) }
}
