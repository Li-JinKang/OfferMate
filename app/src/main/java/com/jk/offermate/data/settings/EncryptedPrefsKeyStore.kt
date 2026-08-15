package com.jk.offermate.data.settings

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * 基于 EncryptedSharedPreferences 的安全 Key 存储：DeepSeek Key 加密落盘，绝不明文。
 */
class EncryptedPrefsKeyStore(context: Context) : SecureKeyStore {

    private val prefs by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "offermate_secure_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    override fun getDeepSeekApiKey(): String = prefs.getString(KEY_DEEPSEEK, "").orEmpty()

    override fun setDeepSeekApiKey(key: String) {
        prefs.edit().putString(KEY_DEEPSEEK, key).apply()
    }

    private companion object {
        const val KEY_DEEPSEEK = "deepseek_api_key"
    }
}
