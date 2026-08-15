package com.jk.offermate.data.local

import com.jk.offermate.data.ai.AnsweredQuestion
import com.jk.offermate.data.ai.Difficulty
import com.jk.offermate.data.local.entity.ImportedPostEntity
import com.jk.offermate.domain.model.PostBadge
import com.jk.offermate.domain.model.Platform
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PostMappersTest {

    @Test
    fun `relativeTimeLabel buckets`() {
        val now = 1_000_000_000_000L
        assertEquals("刚刚", PostMappers.relativeTimeLabel(now, now))
        assertEquals("5分钟前", PostMappers.relativeTimeLabel(now - 5 * 60_000, now))
        assertEquals("2小时前", PostMappers.relativeTimeLabel(now - 2 * 3_600_000, now))
        assertEquals("3天前", PostMappers.relativeTimeLabel(now - 3 * 86_400_000L, now))
    }

    @Test
    fun `question round trips through entities`() {
        val original = listOf(
            AnsweredQuestion(
                question = "Q1",
                answer = "A1",
                tags = listOf("Android", "生命周期"),
                difficulty = Difficulty.MEDIUM,
                keyPoints = listOf("要点1", "要点2"),
                relevanceScore = 90,
                relevanceReason = "相关"
            )
        )

        val entities = PostMappers.toQuestionEntities("post1", original)
        val back = entities.map { PostMappers.toAnswered(it) }

        assertEquals("post1_0", entities[0].id)
        assertEquals(original, back)
    }

    @Test
    fun `toDomainPost maps status badge and count`() {
        val done = ImportedPostEntity(
            id = "1", platform = "NOWCODER", url = "u", resolvedUrl = null,
            title = "字节 Android 面经", summary = "摘要", status = "DONE",
            questionCount = 8, importedAt = 0, updatedAt = 0
        )
        val analyzing = done.copy(id = "2", status = "ANALYZING", questionCount = 0)
        val now = 100_000L

        val donePost = PostMappers.toDomainPost(done, now)
        val analyzingPost = PostMappers.toDomainPost(analyzing, now)

        assertEquals(Platform.NOWCODER, donePost.platform)
        assertEquals(8, donePost.parsedQuestionCount)
        assertNull(donePost.badge)
        assertTrue(analyzingPost.badge is PostBadge.Label)
        assertEquals("分析中", (analyzingPost.badge as PostBadge.Label).text)
    }
}
