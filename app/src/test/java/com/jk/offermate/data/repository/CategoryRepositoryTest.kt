package com.jk.offermate.data.repository

import com.jk.offermate.data.local.dao.CategoryDao
import com.jk.offermate.data.local.entity.CategoryEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class CategoryRepositoryTest {

    private class FakeCategoryDao : CategoryDao {
        val items = MutableStateFlow<List<CategoryEntity>>(emptyList())
        override fun observeAll(): Flow<List<CategoryEntity>> = items.map { it.sortedBy(CategoryEntity::createdAt) }
        override suspend fun insert(category: CategoryEntity) {
            items.value = items.value.filterNot { it.name == category.name } + category
        }
        override suspend fun deleteByName(name: String) {
            items.value = items.value.filterNot { it.name == name }
        }
    }

    private val dao = FakeCategoryDao()
    private var clock = 1L
    private val repo = RoomCategoryRepository(dao, now = { clock++ })

    @Test
    fun `add trims and observe returns names in insert order`() = runTest {
        repo.addCategory("Kotlin")
        repo.addCategory("  网络  ")
        assertEquals(listOf("Kotlin", "网络"), repo.observeCategories().first())
    }

    @Test
    fun `blank category is ignored`() = runTest {
        repo.addCategory("   ")
        assertEquals(emptyList<String>(), repo.observeCategories().first())
    }

    @Test
    fun `delete removes category`() = runTest {
        repo.addCategory("Kotlin")
        repo.addCategory("网络")
        repo.deleteCategory("Kotlin")
        assertEquals(listOf("网络"), repo.observeCategories().first())
    }
}
