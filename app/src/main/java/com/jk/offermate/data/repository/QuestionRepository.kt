package com.jk.offermate.data.repository

import com.jk.offermate.agent.pipeline.AnsweredQuestion
import com.jk.offermate.agent.pipeline.Difficulty
import com.jk.offermate.agent.pipeline.QuestionSource
import com.jk.offermate.data.local.PostMappers
import com.jk.offermate.data.local.dao.QuestionDao
import com.jk.offermate.data.local.entity.QuestionEntity
import com.jk.offermate.data.local.toLikePattern
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
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

    /** 搜索题目：题干/答案/标签/考点/分类任一命中（DB LIKE，结果有上限）。 */
    fun search(query: String, limit: Int = 100): Flow<List<AnsweredQuestion>>

    /** 标记某题已刷/未刷。 */
    suspend fun setPracticed(questionId: String, practiced: Boolean)

    /** 更新某题的答案（追问后据讨论重写答案）。 */
    suspend fun updateAnswer(questionId: String, answer: String)

    /** 更新某题的分类（用户手动移动分类）。 */
    suspend fun updateCategory(questionId: String, category: String)

    /** 批量删除题目（分类级联删除时用）。 */
    suspend fun deleteQuestions(ids: List<String>)

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

    override fun search(query: String, limit: Int): Flow<List<AnsweredQuestion>> {
        val kw = query.trim()
        if (kw.isEmpty()) return flowOf(emptyList())
        return questionDao.search(toLikePattern(kw), limit).map { list -> list.map(PostMappers::toAnswered) }
    }

    override suspend fun setPracticed(questionId: String, practiced: Boolean) {
        questionDao.setPracticed(questionId, practiced)
    }

    override suspend fun updateAnswer(questionId: String, answer: String) {
        questionDao.updateAnswer(questionId, answer)
    }

    override suspend fun updateCategory(questionId: String, category: String) {
        if (questionId.isBlank()) return
        questionDao.updateCategory(questionId, category.trim())
    }

    override suspend fun deleteQuestions(ids: List<String>) {
        val valid = ids.filter { it.isNotBlank() }
        if (valid.isEmpty()) return
        questionDao.deleteByIds(valid)
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
                source = QuestionSource.MANUAL.name,
                category = tag
            )
        )
    }

    override suspend fun deleteQuestion(questionId: String) {
        if (questionId.isBlank()) return
        questionDao.deleteById(questionId)
    }
}
