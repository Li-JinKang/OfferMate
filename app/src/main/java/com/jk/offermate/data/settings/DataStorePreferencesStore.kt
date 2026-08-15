package com.jk.offermate.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "offermate_settings")

/**
 * 基于 DataStore 的偏好存储：接口地址/模型按服务商 id 分键存储。
 */
class DataStorePreferencesStore(context: Context) : PreferencesStore {

    private val dataStore = context.settingsDataStore

    override val activeProviderId: Flow<String> =
        dataStore.data.map { it[KEY_ACTIVE] ?: AiProvider.DEEPSEEK.id }

    override val relevanceThreshold: Flow<Int> =
        dataStore.data.map { it[KEY_THRESHOLD] ?: AppSettings.DEFAULT_THRESHOLD }

    override fun model(providerId: String): Flow<String> =
        dataStore.data.map { it[modelKey(providerId)] ?: AiProvider.from(providerId).defaultModel }

    override fun baseUrl(providerId: String): Flow<String> =
        dataStore.data.map { it[baseUrlKey(providerId)] ?: AiProvider.from(providerId).baseUrl }

    override suspend fun setActiveProvider(providerId: String) {
        dataStore.edit { it[KEY_ACTIVE] = providerId }
    }

    override suspend fun setModel(providerId: String, model: String) {
        dataStore.edit { it[modelKey(providerId)] = model }
    }

    override suspend fun setBaseUrl(providerId: String, url: String) {
        dataStore.edit { it[baseUrlKey(providerId)] = url }
    }

    override suspend fun setRelevanceThreshold(value: Int) {
        dataStore.edit { it[KEY_THRESHOLD] = AppSettings.clampThreshold(value) }
    }

    private fun modelKey(id: String) = stringPreferencesKey("model_$id")
    private fun baseUrlKey(id: String) = stringPreferencesKey("base_$id")

    private companion object {
        val KEY_ACTIVE = stringPreferencesKey("active_provider")
        val KEY_THRESHOLD = intPreferencesKey("relevance_threshold")
    }
}
