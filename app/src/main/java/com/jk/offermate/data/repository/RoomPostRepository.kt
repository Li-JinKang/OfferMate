package com.jk.offermate.data.repository

import com.jk.offermate.data.local.PostMappers
import com.jk.offermate.data.local.dao.ImportedPostDao
import com.jk.offermate.data.local.dao.QuestionDao
import com.jk.offermate.domain.model.Post
import com.jk.offermate.domain.repository.PostRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * 基于 Room 的帖子仓库：首页读取真实导入记录，并支持置顶/删除。
 */
class RoomPostRepository(
    private val postDao: ImportedPostDao,
    private val questionDao: QuestionDao,
    private val now: () -> Long = System::currentTimeMillis
) : PostRepository {

    override fun observePosts(category: String?): Flow<List<Post>> =
        postDao.observeAll().map { list ->
            list.map { PostMappers.toDomainPost(it, now()) }
                .let { posts -> if (category == null) posts else posts.filter { it.category == category } }
        }

    override fun categories(): List<String> = emptyList()

    override suspend fun setPinned(id: String, pinned: Boolean) {
        postDao.setPinned(id, pinned, now())
    }

    override suspend fun delete(id: String) {
        questionDao.deleteByPost(id)
        postDao.delete(id)
    }
}
