package com.jk.offermate.data.local

import com.jk.offermate.agent.pipeline.AnsweredQuestion
import com.jk.offermate.data.dedup.QuestionDeduplicator
import com.jk.offermate.data.dedup.QuestionFingerprint
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
    private val deduplicator: QuestionDeduplicator = QuestionDeduplicator(),
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

    /** 分析成功：去重后写入题目并把帖子置为 DONE。 */
    suspend fun saveSuccess(id: String, title: String, summary: String, questions: List<AnsweredQuestion>) {
        questionDao.deleteByPost(id)
        val unique = dedupForInsert(questions)
        questionDao.insertAll(PostMappers.toQuestionEntities(id, unique))
        val existing = postDao.findById(id) ?: return
        postDao.upsert(
            existing.copy(
                title = title.ifBlank { existing.title },
                summary = summary,
                status = ImportStatus.DONE.name,
                questionCount = unique.size,
                updatedAt = now()
            )
        )
    }

    /**
     * 增量去重：丢弃与「已入库同分桶题目」或「本批已保留题目」重复的题。
     * 只查询同分桶候选 + 精确指纹兜底，不扫全表。
     */
    private suspend fun dedupForInsert(questions: List<AnsweredQuestion>): List<AnsweredQuestion> {
        if (questions.isEmpty()) return questions
        val fingerprints = questions.map { deduplicator.fingerprint(it.question, it.tags) }

        val buckets = fingerprints.map { it.bucketKey }.distinct()
        val existing = questionDao.fingerprintsInBuckets(buckets)
            .map { QuestionFingerprint(it.exactHash, it.simhash, it.bucketKey) }
        val existingExact = questionDao
            .existingExactHashes(fingerprints.map { it.exactHash }.filter { it.isNotEmpty() }.distinct())
            .toHashSet()

        val kept = ArrayList<AnsweredQuestion>()
        val keptFingerprints = ArrayList<QuestionFingerprint>()
        for (i in questions.indices) {
            val fp = fingerprints[i]
            val dupExisting = fp.exactHash in existingExact || existing.any { deduplicator.isDuplicate(fp, it) }
            val dupBatch = keptFingerprints.any { deduplicator.isDuplicate(fp, it) }
            if (!dupExisting && !dupBatch) {
                kept += questions[i]
                keptFingerprints += fp
            }
        }
        return kept
    }

    suspend fun markNeedsManual(id: String) = markStatus(id, ImportStatus.NEEDS_MANUAL_INPUT)

    suspend fun markFailed(id: String) = markStatus(id, ImportStatus.FAILED)

    suspend fun delete(id: String) {
        questionDao.deleteByPost(id)
        postDao.delete(id)
    }
}
