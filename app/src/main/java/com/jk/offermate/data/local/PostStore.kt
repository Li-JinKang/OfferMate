package com.jk.offermate.data.local

import com.jk.offermate.data.ai.AnsweredQuestion
import com.jk.offermate.data.importer.PlatformDetector
import com.jk.offermate.data.local.dao.ImportedPostDao
import com.jk.offermate.data.local.dao.QuestionDao
import com.jk.offermate.data.local.entity.ImportedPostEntity
import com.jk.offermate.domain.model.ImportStatus

/**
 * 导入帖子的写入编排：状态流转与结果落库，供后台 Worker 调用。
 * 依赖 DAO 抽象，可用 fake DAO 做 JVM 单测。
 */
class PostStore(
    private val postDao: ImportedPostDao,
    private val questionDao: QuestionDao,
    private val now: () -> Long = System::currentTimeMillis
) {

    /** 分享/提取入队时先落一条待处理记录。 */
    suspend fun createPending(id: String, url: String) {
        val t = now()
        postDao.upsert(
            ImportedPostEntity(
                id = id,
                platform = PlatformDetector.detect(url).name,
                url = url,
                resolvedUrl = null,
                title = "",
                summary = "",
                status = ImportStatus.PENDING.name,
                questionCount = 0,
                importedAt = t,
                updatedAt = t
            )
        )
    }

    suspend fun markStatus(id: String, status: ImportStatus) {
        postDao.updateStatus(id, status.name, now())
    }

    suspend fun setPinned(id: String, pinned: Boolean) {
        postDao.setPinned(id, pinned, now())
    }

    /** 分析成功：写入题目并把帖子置为 DONE。 */
    suspend fun saveSuccess(id: String, title: String, summary: String, questions: List<AnsweredQuestion>) {
        questionDao.deleteByPost(id)
        questionDao.insertAll(PostMappers.toQuestionEntities(id, questions))
        val existing = postDao.findById(id) ?: return
        postDao.upsert(
            existing.copy(
                title = title.ifBlank { existing.title },
                summary = summary,
                status = ImportStatus.DONE.name,
                questionCount = questions.size,
                updatedAt = now()
            )
        )
    }

    suspend fun markNeedsManual(id: String) = markStatus(id, ImportStatus.NEEDS_MANUAL_INPUT)

    suspend fun markFailed(id: String) = markStatus(id, ImportStatus.FAILED)

    suspend fun delete(id: String) {
        questionDao.deleteByPost(id)
        postDao.delete(id)
    }
}
