package com.jk.offermate.data.repository

import com.jk.offermate.data.ai.AnsweredQuestion
import com.jk.offermate.data.local.PostMappers
import com.jk.offermate.data.local.dao.QuestionDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** 题目查询仓库（题目页读取某帖子的题目）。 */
interface QuestionRepository {
    fun observeByPost(postId: String): Flow<List<AnsweredQuestion>>
}

class RoomQuestionRepository(private val questionDao: QuestionDao) : QuestionRepository {
    override fun observeByPost(postId: String): Flow<List<AnsweredQuestion>> =
        questionDao.observeByPost(postId).map { list -> list.map(PostMappers::toAnswered) }
}
