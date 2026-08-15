package com.jk.offermate.data.settings

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * 基于 EncryptedSharedPreferences 的安全 Key 存储：按服务商 id 分别加密存 Key。
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

    override fun getApiKey(providerId: String): String =
        prefs.getString(keyName(providerId), "").orEmpty()

    override fun setApiKey(providerId: String, key: String) {
        prefs.edit().putString(keyName(providerId), key).apply()
    }

    private fun keyName(providerId: String) = "api_key_$providerId"
}
