package com.jk.offermate.data.repository

import com.jk.offermate.data.local.dao.CategoryDao
import com.jk.offermate.data.local.entity.CategoryEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/** 用户自定义分类仓库。 */
interface CategoryRepository {
    /** 用户手动创建的分类名（有序）。 */
    fun observeCategories(): Flow<List<String>>

    suspend fun addCategory(name: String)

    suspend fun deleteCategory(name: String)

    /** 题库分类的用户自定义显示顺序（拼图排序）；未包含的分类视为排在其后。 */
    fun observeOrder(): Flow<List<String>>

    /** 保存题库分类的显示顺序。 */
    suspend fun saveOrder(names: List<String>)
}

class RoomCategoryRepository(
    private val dao: CategoryDao,
    private val orderStore: CategoryOrderStore? = null,
    private val now: () -> Long = { System.currentTimeMillis() }
) : CategoryRepository {

    override fun observeCategories(): Flow<List<String>> =
        dao.observeAll().map { list -> list.map { it.name } }

    override suspend fun addCategory(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        dao.insert(CategoryEntity(name = trimmed, createdAt = now()))
    }

    override suspend fun deleteCategory(name: String) {
        dao.deleteByName(name.trim())
    }

    override fun observeOrder(): Flow<List<String>> =
        orderStore?.order ?: flowOf(emptyList())

    override suspend fun saveOrder(names: List<String>) {
        orderStore?.saveOrder(names)
    }
}
