package com.jk.offermate.data.repository

import com.jk.offermate.domain.model.Platform
import com.jk.offermate.domain.model.Post
import com.jk.offermate.domain.model.PostBadge
import com.jk.offermate.domain.repository.PostRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * 临时的样例数据实现，用于在真实分析流水线接通前驱动 UI。
 * 后续会替换为基于 Room + 分析结果的真实实现（接口不变，UI 无需改动）。
 */
class FakePostRepository : PostRepository {

    private val samples = listOf(
        Post(
            id = "1",
            platform = Platform.NOWCODER,
            title = "字节跳动 - Java后端二面热乎复盘（已意向）",
            summary = "主要考察微服务治理、JVM 调优以及 MySQL 深分页优化，包含 Redis 缓存雪崩的真实实战场景…",
            timeLabel = "2小时前",
            category = "Java后端",
            parsedQuestionCount = 8,
            badge = PostBadge.ResumeMatch(95)
        ),
        Post(
            id = "2",
            platform = Platform.XIAOHONGSHU,
            title = "腾讯大厂前端高频100问整理（附标准答案）",
            summary = "整理了针对 Vue3 底层原理、React Fiber 架构以及浏览器渲染机制的真题，适合社招/校招通关…",
            timeLabel = "昨天",
            category = "前端开发",
            parsedQuestionCount = 12,
            badge = PostBadge.Label("高频必背")
        )
    )

    override fun observePosts(category: String?): Flow<List<Post>> {
        val filtered = if (category == null) samples else samples.filter { it.category == category }
        return flowOf(filtered)
    }

    override fun categories(): List<String> =
        samples.map { it.category }.distinct()
}
