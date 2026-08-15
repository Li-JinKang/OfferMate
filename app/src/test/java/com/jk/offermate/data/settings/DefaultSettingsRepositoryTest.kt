package com.jk.offermate.data.settings

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultSettingsRepositoryTest {

    private class FakeSecureKeyStore : SecureKeyStore {
        private val keys = mutableMapOf<String, String>()
        override fun getApiKey(providerId: String): String = keys[providerId].orEmpty()
        override fun setApiKey(providerId: String, key: String) { keys[providerId] = key }
    }

    private class FakePreferencesStore : PreferencesStore {
        val active = MutableStateFlow(AiProvider.DEEPSEEK.id)
        val threshold = MutableStateFlow(AppSettings.DEFAULT_THRESHOLD)
        val models = MutableStateFlow<Map<String, String>>(emptyMap())
        val baseUrls = MutableStateFlow<Map<String, String>>(emptyMap())

        override val activeProviderId: Flow<String> = active
        override val relevanceThreshold: Flow<Int> = threshold
        override fun model(providerId: String): Flow<String> =
            models.map { it[providerId] ?: AiProvider.from(providerId).defaultModel }
        override fun baseUrl(providerId: String): Flow<String> =
            baseUrls.map { it[providerId] ?: AiProvider.from(providerId).baseUrl }

        override suspend fun setActiveProvider(providerId: String) { active.value = providerId }
        override suspend fun setModel(providerId: String, model: String) { models.value = models.value + (providerId to model) }
        override suspend fun setBaseUrl(providerId: String, url: String) { baseUrls.value = baseUrls.value + (providerId to url) }
        override suspend fun setRelevanceThreshold(value: Int) { threshold.value = value }
    }

    private fun repo(
        keyStore: FakeSecureKeyStore = FakeSecureKeyStore(),
        prefs: FakePreferencesStore = FakePreferencesStore()
    ) = DefaultSettingsRepository(keyStore, prefs)

    @Test
    fun `defaults expose deepseek not configured`() = runTest {
        val repository = repo()
        val active = repository.activeConfig.first()

        assertEquals(AiProvider.DEEPSEEK.id, active.providerId)
        assertEquals(AiProvider.DEEPSEEK.defaultModel, active.model)
        assertEquals(AiProvider.DEEPSEEK.baseUrl, active.baseUrl)
        assertFalse(active.isConfigured)
    }

    @Test
    fun `enableProvider saves per-provider config and switches active`() = runTest {
        val repository = repo()

        repository.enableProvider(AiProvider.KIMI.id, "  sk-kimi  ", "moonshot-v1-32k", "https://api.moonshot.cn/v1/")
        val active = repository.activeConfig.first()

        assertEquals(AiProvider.KIMI.id, active.providerId)
        assertEquals("sk-kimi", active.apiKey) // 去空格
        assertEquals("moonshot-v1-32k", active.model)
        assertTrue(active.isConfigured)
    }

    @Test
    fun `each provider keeps its own key`() = runTest {
        val repository = repo()

        repository.enableProvider(AiProvider.DEEPSEEK.id, "sk-ds", "deepseek-chat", "https://api.deepseek.com/")
        repository.enableProvider(AiProvider.GLM.id, "sk-glm", "glm-4-flash", "https://open.bigmodel.cn/api/paas/v4/")

        assertEquals("sk-ds", repository.config(AiProvider.DEEPSEEK.id).first().apiKey)
        assertEquals("sk-glm", repository.config(AiProvider.GLM.id).first().apiKey)
        // 当前启用为最后启用的 GLM
        assertEquals(AiProvider.GLM.id, repository.activeConfig.first().providerId)
    }

    @Test
    fun `threshold clamps`() = runTest {
        val repository = repo()
        repository.updateRelevanceThreshold(999)
        assertEquals(AppSettings.MAX_THRESHOLD, repository.relevanceThreshold.first())
    }
}
