package com.jk.offermate.data.local

import com.jk.offermate.agent.AnsweredQuestion
import com.jk.offermate.agent.Difficulty
import com.jk.offermate.agent.QuestionSource
import com.jk.offermate.data.dedup.QuestionDeduplicator
import com.jk.offermate.data.local.entity.ImportedPostEntity
import com.jk.offermate.data.local.entity.QuestionEntity
import com.jk.offermate.domain.model.ImportStatus
import com.jk.offermate.domain.model.Platform
import com.jk.offermate.domain.model.Post
import com.jk.offermate.domain.model.PostBadge

/**
 * Room 实体与领域模型之间的映射（纯函数，可 JVM 单测）。
 */
object PostMappers {

    private const val SEP = "\n"

    fun toDomainPost(entity: ImportedPostEntity, nowMillis: Long): Post {
        val status = ImportStatus.from(entity.status)
        val platform = parsePlatform(entity.platform)
        return Post(
            id = entity.id,
            platform = platform,
            title = entity.title.ifBlank { "未命名帖子" },
            summary = entity.summary,
            timeLabel = relativeTimeLabel(entity.importedAt, nowMillis),
            category = platformName(platform),
            parsedQuestionCount = entity.questionCount,
            badge = if (status == ImportStatus.DONE) null else PostBadge.Label(status.label),
            pinned = entity.pinned
        )
    }

    fun toAnswered(entity: QuestionEntity): AnsweredQuestion = AnsweredQuestion(
        question = entity.question,
        answer = entity.answer,
        tags = splitLines(entity.tagsCsv),
        difficulty = Difficulty.from(entity.difficulty),
        keyPoints = splitLines(entity.keyPointsCsv),
        relevanceScore = entity.relevanceScore,
        relevanceReason = entity.relevanceReason,
        id = entity.id,
        practiced = entity.practiced,
        source = QuestionSource.from(entity.source),
        category = entity.category
    )

    private val deduplicator = QuestionDeduplicator()

    fun toQuestionEntities(postId: String, questions: List<AnsweredQuestion>): List<QuestionEntity> =
        questions.mapIndexed { index, q ->
            val fp = deduplicator.fingerprint(q.question, q.tags)
            QuestionEntity(
                id = "${postId}_$index",
                postId = postId,
                orderIndex = index,
                question = q.question,
                answer = q.answer,
                tagsCsv = q.tags.joinToString(SEP),
                difficulty = q.difficulty.name,
                keyPointsCsv = q.keyPoints.joinToString(SEP),
                relevanceScore = q.relevanceScore,
                relevanceReason = q.relevanceReason,
                exactHash = fp.exactHash,
                simhash = fp.simhash,
                bucketKey = fp.bucketKey,
                category = q.category
            )
        }

    fun relativeTimeLabel(thenMillis: Long, nowMillis: Long): String {
        val diff = (nowMillis - thenMillis).coerceAtLeast(0)
        return when {
            diff < 60_000 -> "刚刚"
            diff < 3_600_000 -> "${diff / 60_000}分钟前"
            diff < 86_400_000 -> "${diff / 3_600_000}小时前"
            else -> "${diff / 86_400_000}天前"
        }
    }

    private fun splitLines(s: String): List<String> =
        s.split(SEP).map { it.trim() }.filter { it.isNotEmpty() }

    private fun parsePlatform(name: String): Platform =
        runCatching { Platform.valueOf(name) }.getOrDefault(Platform.NOWCODER)

    private fun platformName(p: Platform): String = when (p) {
        Platform.NOWCODER -> "牛客"
        Platform.XIAOHONGSHU -> "小红书"
    }
}
