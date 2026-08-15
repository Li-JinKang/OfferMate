package com.jk.offermate.domain.repository

import com.jk.offermate.domain.model.Post
import kotlinx.coroutines.flow.Flow

/**
 * 帖子数据仓库（依赖倒置：UI/领域层只依赖此抽象，具体数据来源可替换）。
 */
interface PostRepository {

    /**
     * 观察帖子列表。
     * @param category 分类筛选；null 表示全部来源。
     */
    fun observePosts(category: String?): Flow<List<Post>>

    /** 可用于筛选的分类（不含"全部来源"）。 */
    fun categories(): List<String>
}
