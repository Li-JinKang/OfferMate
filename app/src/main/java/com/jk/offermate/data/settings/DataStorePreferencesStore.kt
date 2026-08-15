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
 * 基于 DataStore 的普通偏好存储（模型名、相关性阈值）。
 */
class DataStorePreferencesStore(context: Context) : PreferencesStore {

    private val dataStore = context.settingsDataStore

    override val model: Flow<String> = dataStore.data.map { prefs ->
        prefs[KEY_MODEL] ?: AppSettings.DEFAULT_MODEL
    }

    override val relevanceThreshold: Flow<Int> = dataStore.data.map { prefs ->
        prefs[KEY_THRESHOLD] ?: AppSettings.DEFAULT_THRESHOLD
    }

    override suspend fun setModel(model: String) {
        dataStore.edit { it[KEY_MODEL] = model }
    }

    override suspend fun setRelevanceThreshold(value: Int) {
        dataStore.edit { it[KEY_THRESHOLD] = AppSettings.clampThreshold(value) }
    }

    private companion object {
        val KEY_MODEL = stringPreferencesKey("model")
        val KEY_THRESHOLD = intPreferencesKey("relevance_threshold")
    }
}
