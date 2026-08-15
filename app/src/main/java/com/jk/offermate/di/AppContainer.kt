package com.jk.offermate.di

import android.content.Context
import androidx.room.Room
import com.jk.offermate.data.local.OfferMateDatabase
import com.jk.offermate.data.repository.FakePostRepository
import com.jk.offermate.data.settings.DataStorePreferencesStore
import com.jk.offermate.data.settings.DefaultSettingsRepository
import com.jk.offermate.data.settings.EncryptedPrefsKeyStore
import com.jk.offermate.data.settings.SettingsRepository
import com.jk.offermate.domain.repository.PostRepository

/**
 * 应用级依赖容器（手动依赖注入组合根）。集中构造并持有依赖，向上暴露接口。
 */
interface AppContainer {
    val postRepository: PostRepository
    val settingsRepository: SettingsRepository
}

class DefaultAppContainer(private val context: Context) : AppContainer {

    private val database: OfferMateDatabase by lazy {
        Room.databaseBuilder(context, OfferMateDatabase::class.java, "offermate.db").build()
    }

    override val postRepository: PostRepository by lazy { FakePostRepository() }

    override val settingsRepository: SettingsRepository by lazy {
        DefaultSettingsRepository(
            secureKeyStore = EncryptedPrefsKeyStore(context),
            preferencesStore = DataStorePreferencesStore(context)
        )
    }
}
