package com.jk.offermate.data.local

import com.jk.offermate.agent.pipeline.AnsweredQuestion
import com.jk.offermate.agent.pipeline.Difficulty
import com.jk.offermate.data.local.entity.ImportedPostEntity
import com.jk.offermate.domain.model.ImportStatus
import com.jk.offermate.domain.model.PostBadge
import com.jk.offermate.domain.model.Platform
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
        assertEquals(original[0].question, back[0].question)
        assertEquals(original[0].answer, back[0].answer)
        assertEquals(original[0].tags, back[0].tags)
        assertEquals(original[0].difficulty, back[0].difficulty)
        assertEquals(original[0].keyPoints, back[0].keyPoints)
        assertEquals("post1_0", back[0].id)
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

    @Test
    fun `toDomainPost surfaces failure reason and retry affordance`() {
        val failed = ImportedPostEntity(
            id = "1", platform = "XIAOHONGSHU", url = "https://xhslink.cn/x", resolvedUrl = null,
            title = "面经", summary = "", status = "FAILED",
            questionCount = 0, importedAt = 0, updatedAt = 0,
            failureReason = "网络连接被中断"
        )

        val post = PostMappers.toDomainPost(failed, 0)

        assertEquals(ImportStatus.FAILED, post.status)
        assertEquals("网络连接被中断", post.failureReason)
        assertEquals("https://xhslink.cn/x", post.sourceUrl)
        assertTrue(post.canRetry)
    }

    @Test
    fun `manual paste record cannot be retried by url`() {
        val failed = ImportedPostEntity(
            id = "1", platform = "NOWCODER", url = "手动粘贴", resolvedUrl = null,
            title = "面经", summary = "", status = "FAILED",
            questionCount = 0, importedAt = 0, updatedAt = 0,
            failureReason = "模型返回格式异常"
        )

        val post = PostMappers.toDomainPost(failed, 0)

        assertFalse(post.canRetry)
        assertEquals("模型返回格式异常", post.failureReason)
    }

    @Test
    fun `done post exposes no failure reason`() {
        val done = ImportedPostEntity(
            id = "1", platform = "NOWCODER", url = "https://www.nowcoder.com/x", resolvedUrl = null,
            title = "面经", summary = "", status = "DONE",
            questionCount = 3, importedAt = 0, updatedAt = 0
        )

        val post = PostMappers.toDomainPost(done, 0)

        assertNull(post.failureReason)
        assertFalse(post.canRetry)
    }

    @Test
    fun `resolved url wins over raw url as retry target`() {
        val failed = ImportedPostEntity(
            id = "1", platform = "XIAOHONGSHU", url = "https://xhslink.cn/x",
            resolvedUrl = "https://www.xiaohongshu.com/explore/abc",
            title = "面经", summary = "", status = "NEEDS_MANUAL_INPUT",
            questionCount = 0, importedAt = 0, updatedAt = 0,
            failureReason = "页面抓取失败"
        )

        val post = PostMappers.toDomainPost(failed, 0)

        assertEquals("https://www.xiaohongshu.com/explore/abc", post.sourceUrl)
        assertTrue(post.canRetry)
    }
}
