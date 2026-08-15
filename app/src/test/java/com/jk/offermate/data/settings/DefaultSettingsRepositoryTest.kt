package com.jk.offermate.data.settings

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultSettingsRepositoryTest {

    private class FakeSecureKeyStore(private var key: String = "") : SecureKeyStore {
        override fun getDeepSeekApiKey(): String = key
        override fun setDeepSeekApiKey(key: String) { this.key = key }
    }

    private class FakePreferencesStore : PreferencesStore {
        val providerState = MutableStateFlow(AiProvider.DEEPSEEK.id)
        val baseUrlState = MutableStateFlow(AiProvider.DEEPSEEK.baseUrl)
        val modelState = MutableStateFlow(AiProvider.DEEPSEEK.defaultModel)
        val thresholdState = MutableStateFlow(AppSettings.DEFAULT_THRESHOLD)
        override val provider: Flow<String> = providerState
        override val baseUrl: Flow<String> = baseUrlState
        override val model: Flow<String> = modelState
        override val relevanceThreshold: Flow<Int> = thresholdState
        override suspend fun setProvider(providerId: String) { providerState.value = providerId }
        override suspend fun setBaseUrl(url: String) { baseUrlState.value = url }
        override suspend fun setModel(model: String) { modelState.value = model }
        override suspend fun setRelevanceThreshold(value: Int) { thresholdState.value = value }
    }

    private fun repo(
        keyStore: FakeSecureKeyStore = FakeSecureKeyStore(),
        prefs: FakePreferencesStore = FakePreferencesStore()
    ) = DefaultSettingsRepository(keyStore, prefs)

    @Test
    fun `defaults are exposed and key not configured`() = runTest {
        val settings = repo().settings.first()

        assertEquals(AiProvider.DEEPSEEK.defaultModel, settings.model)
        assertEquals(AppSettings.DEFAULT_THRESHOLD, settings.relevanceThreshold)
        assertTrue(settings.deepSeekApiKey.isEmpty())
        assertFalse(settings.isDeepSeekConfigured)
    }

    @Test
    fun `updateApiKey trims and marks configured`() = runTest {
        val repository = repo()

        repository.updateApiKey("  sk-deepseek-123  ")
        val settings = repository.settings.first()

        assertEquals("sk-deepseek-123", settings.deepSeekApiKey)
        assertTrue(settings.isDeepSeekConfigured)
    }

    @Test
    fun `updateModel is reflected`() = runTest {
        val repository = repo()

        repository.updateModel("deepseek-reasoner")

        assertEquals("deepseek-reasoner", repository.settings.first().model)
    }

    @Test
    fun `updateRelevanceThreshold clamps out-of-range values`() = runTest {
        val repository = repo()

        repository.updateRelevanceThreshold(150)
        assertEquals(AppSettings.MAX_THRESHOLD, repository.settings.first().relevanceThreshold)

        repository.updateRelevanceThreshold(-20)
        assertEquals(AppSettings.MIN_THRESHOLD, repository.settings.first().relevanceThreshold)
    }

    @Test
    fun `updateProvider sets base url and default model for non-custom`() = runTest {
        val repository = repo()

        repository.updateProvider(AiProvider.KIMI)
        val settings = repository.settings.first()

        assertEquals(AiProvider.KIMI.id, settings.providerId)
        assertEquals(AiProvider.KIMI.baseUrl, settings.baseUrl)
        assertEquals(AiProvider.KIMI.defaultModel, settings.model)
    }

    @Test
    fun `updateProvider custom keeps base url editable`() = runTest {
        val repository = repo()

        repository.updateProvider(AiProvider.CUSTOM)
        repository.updateBaseUrl("https://my.example.com/v1/")
        val settings = repository.settings.first()

        assertEquals(AiProvider.CUSTOM.id, settings.providerId)
        assertEquals("https://my.example.com/v1/", settings.baseUrl)
    }

    @Test
    fun `settings clamps threshold read from store`() = runTest {
        val prefs = FakePreferencesStore()
        val repository = repo(prefs = prefs)

        // 绕过仓库直接写入越界值，settings 读取时应被 clamp
        prefs.thresholdState.value = 999

        assertEquals(AppSettings.MAX_THRESHOLD, repository.settings.first().relevanceThreshold)
    }
}
