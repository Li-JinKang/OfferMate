package com.jk.offermate.data.resume

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.jk.offermate.agent.resume.ResumeProfile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.resumeDataStore: DataStore<Preferences> by preferencesDataStore(name = "offermate_resume")

/**
 * 基于 DataStore 的简历画像存储（MVP：单份简历）。
 */
class DataStoreResumeRepository(context: Context) : ResumeRepository {

    private val dataStore = context.resumeDataStore

    override val profile: Flow<ResumeProfile> = dataStore.data.map { prefs ->
        val skillsCsv = prefs[KEY_SKILLS].orEmpty()
        ResumeProfile(
            targetRole = prefs[KEY_TARGET_ROLE].orEmpty(),
            skills = ResumeRepository.parseSkills(skillsCsv),
            rawText = prefs[KEY_RAW_TEXT].orEmpty()
        )
    }

    override val resumeFilePath: Flow<String?> = dataStore.data.map { prefs ->
        prefs[KEY_FILE_PATH]?.takeIf { it.isNotBlank() }
    }

    override val needsAiAnalysis: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[KEY_NEEDS_AI_ANALYSIS] ?: false
    }

    override suspend fun save(targetRole: String, skillsCsv: String, rawText: String) {
        dataStore.edit { prefs ->
            prefs[KEY_TARGET_ROLE] = targetRole.trim()
            prefs[KEY_SKILLS] = skillsCsv.trim()
            prefs[KEY_RAW_TEXT] = rawText.trim()
        }
    }

    override suspend fun updateRawText(rawText: String) {
        dataStore.edit { prefs -> prefs[KEY_RAW_TEXT] = rawText.trim() }
    }

    override suspend fun setFilePath(path: String?) {
        dataStore.edit { prefs ->
            if (path.isNullOrBlank()) prefs.remove(KEY_FILE_PATH) else prefs[KEY_FILE_PATH] = path
        }
    }

    override suspend fun setNeedsAiAnalysis(needs: Boolean) {
        dataStore.edit { prefs -> prefs[KEY_NEEDS_AI_ANALYSIS] = needs }
    }

    private companion object {
        val KEY_TARGET_ROLE = stringPreferencesKey("target_role")
        val KEY_SKILLS = stringPreferencesKey("skills_csv")
        val KEY_RAW_TEXT = stringPreferencesKey("raw_text")
        val KEY_FILE_PATH = stringPreferencesKey("resume_file_path")
        val KEY_NEEDS_AI_ANALYSIS = booleanPreferencesKey("needs_ai_analysis")
    }
}
