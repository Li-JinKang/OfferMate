package com.jk.offermate.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.answerUpdateDataStore: DataStore<Preferences> by
    preferencesDataStore(name = "offermate_answer_update")

/**
 * 持久化「某会话上次用讨论更新答案时的消息条数」。
 *
 * 用于限制同一段讨论只更新一次答案：只有会话消息数超过该记录（即又产生了新对话）才允许再次更新。
 * 放在 DataStore 而非 Room，是为了避免为一个小标记去改库版本（当前库用 destructiveMigration，
 * 升级会清空用户数据）。按会话 id 存一个 int，缺省 -1（从未更新过）。
 */
class AnswerUpdateStore(context: Context) {

    private val dataStore = context.answerUpdateDataStore

    fun observe(conversationId: String): Flow<Int> =
        dataStore.data.map { it[key(conversationId)] ?: -1 }

    suspend fun set(conversationId: String, messageCount: Int) {
        dataStore.edit { it[key(conversationId)] = messageCount }
    }

    private fun key(conversationId: String) = intPreferencesKey("conv_$conversationId")
}
