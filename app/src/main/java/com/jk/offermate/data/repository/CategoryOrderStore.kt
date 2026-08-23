package com.jk.offermate.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.categoryOrderDataStore: DataStore<Preferences> by
    preferencesDataStore(name = "offermate_category_order")

/**
 * 题库分类的用户自定义显示顺序（拼图排序）持久化。
 *
 * 分类由「题目派生 + 用户创建」混合而成、以名称标识，没有天然的排序列，故用 DataStore 存一份
 * 有序的名称清单。渲染时按此清单排序，未在清单中的新分类排到末尾。以 `\n` 连接存储（分类名不含换行）。
 */
class CategoryOrderStore(context: Context) {

    private val dataStore = context.categoryOrderDataStore

    val order: Flow<List<String>> = dataStore.data.map { prefs ->
        prefs[KEY_ORDER]?.split("\n")?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()
    }

    suspend fun saveOrder(names: List<String>) {
        val cleaned = names.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        dataStore.edit { it[KEY_ORDER] = cleaned.joinToString("\n") }
    }

    private companion object {
        val KEY_ORDER = stringPreferencesKey("category_order")
    }
}
