package com.jk.offermate.ui.home

import com.jk.offermate.agent.resume.ResumeProfile
import com.jk.offermate.data.repository.FakePostRepository
import com.jk.offermate.data.resume.ResumeRepository
import com.jk.offermate.data.settings.ProviderConfig
import com.jk.offermate.data.settings.SettingsRepository
import com.jk.offermate.work.ImportScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class HomeViewModelTest {

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private class FakeImportScheduler : ImportScheduler {
        val urls = mutableListOf<String>()
        val texts = mutableListOf<String>()
        override suspend fun enqueueUrl(url: String): String {
            urls += url
            return "id-${urls.size}"
        }
        override suspend fun enqueueText(text: String, sourceUrl: String): String {
            texts += text
            return "id-t-${texts.size}"
        }
    }

    private class FakeResumeRepository(profile: ResumeProfile) : ResumeRepository {
        private val state = MutableStateFlow(profile)
        override val profile: Flow<ResumeProfile> = state
        override val resumeFilePath: Flow<String?> = MutableStateFlow(null)
        override suspend fun save(targetRole: String, skillsCsv: String, rawText: String) {}
        override suspend fun updateRawText(rawText: String) {}
        override suspend fun setFilePath(path: String?) {}
    }

    private class FakeSettingsRepository(apiKey: String) : SettingsRepository {
        private val cfg = ProviderConfig(providerId = "deepseek", apiKey = apiKey, model = "m", baseUrl = "b")
        override val activeProviderId: Flow<String> = MutableStateFlow(cfg.providerId)
        override val relevanceThreshold: Flow<Int> = MutableStateFlow(70)
        override val activeConfig: Flow<ProviderConfig> = MutableStateFlow(cfg)
        override val mcpServers: Flow<List<com.jk.offermate.agent.mcp.McpServerConfig>> = MutableStateFlow(emptyList())
        override fun config(providerId: String): Flow<ProviderConfig> = MutableStateFlow(cfg)
        override suspend fun enableProvider(providerId: String, apiKey: String, model: String, baseUrl: String) {}
        override suspend fun updateRelevanceThreshold(value: Int) {}
        override suspend fun addMcpServer(name: String, url: String, headers: Map<String, String>) {}
        override suspend fun removeMcpServer(name: String) {}
        override suspend fun setMcpServerEnabled(name: String, enabled: Boolean) {}
    }

    private fun viewModel(
        scheduler: FakeImportScheduler = FakeImportScheduler(),
        resumeText: String = "我的简历内容",
        apiKey: String = "sk-test"
    ) = HomeViewModel(
        postRepository = FakePostRepository(),
        importScheduler = scheduler,
        resumeRepository = FakeResumeRepository(ResumeProfile(targetRole = "", rawText = resumeText)),
        settingsRepository = FakeSettingsRepository(apiKey)
    )

    @Test
    fun `extract without api key shows hint and does not enqueue`() {
        val scheduler = FakeImportScheduler()
        val vm = viewModel(scheduler, apiKey = "")
        vm.onLinkChange("https://www.nowcoder.com/x")

        vm.onExtract()

        assertTrue(scheduler.urls.isEmpty())
        assertTrue(vm.uiState.value.message!!.contains("API Key"))
    }

    @Test
    fun `extract without resume still enqueues and shows toast`() {
        val scheduler = FakeImportScheduler()
        val vm = viewModel(scheduler, resumeText = "")
        vm.onLinkChange("https://www.nowcoder.com/x")

        vm.onExtract()

        assertEquals(listOf("https://www.nowcoder.com/x"), scheduler.urls)
        assertTrue(vm.uiState.value.toast!!.contains("简历"))
    }

    @Test
    fun `extract enqueues url and clears input`() {
        val scheduler = FakeImportScheduler()
        val vm = viewModel(scheduler)
        vm.onLinkChange("https://www.nowcoder.com/x")

        vm.onExtract()

        assertEquals(listOf("https://www.nowcoder.com/x"), scheduler.urls)
        assertEquals("", vm.uiState.value.linkInput)
        assertTrue(vm.uiState.value.message!!.contains("后台"))
    }

    @Test
    fun `extract with title and link mixed text extracts pure link`() {
        val scheduler = FakeImportScheduler()
        val vm = viewModel(scheduler)
        vm.onLinkChange(
            "拼多多客户端二面凉经 深深的八股拷打，答不上来的题... https://xhslink.cn/o/8XYgQ9UCwdT " +
                "先复制再打开【小红书】，精彩内容等你翻阅。"
        )

        vm.onExtract()

        assertEquals(listOf("https://xhslink.cn/o/8XYgQ9UCwdT"), scheduler.urls)
    }

    @Test
    fun `paste analyze enqueues text`() {
        val scheduler = FakeImportScheduler()
        val vm = viewModel(scheduler)

        vm.onPasteAnalyze("一段粘贴的正文")

        assertEquals(listOf("一段粘贴的正文"), scheduler.texts)
    }

    @Test
    fun `shared text with link enqueues url`() {
        val scheduler = FakeImportScheduler()
        val vm = viewModel(scheduler)

        vm.onSharedTextReceived("这道面试题真难 https://xhslink.cn/o/6Gz0nDGZxAE 大家来看看")

        assertEquals(listOf("https://xhslink.cn/o/6Gz0nDGZxAE"), scheduler.urls)
        assertTrue(scheduler.texts.isEmpty())
        assertTrue(vm.uiState.value.message!!.contains("分享"))
    }

    @Test
    fun `shared text without link enqueues as manual text`() {
        val scheduler = FakeImportScheduler()
        val vm = viewModel(scheduler)

        vm.onSharedTextReceived("一段没有链接的面经正文")

        assertEquals(listOf("一段没有链接的面经正文"), scheduler.texts)
        assertTrue(scheduler.urls.isEmpty())
    }

    @Test
    fun `shared text without api key shows hint and does not enqueue`() {
        val scheduler = FakeImportScheduler()
        val vm = viewModel(scheduler, apiKey = "")

        vm.onSharedTextReceived("https://www.nowcoder.com/x")

        assertTrue(scheduler.urls.isEmpty())
        assertTrue(vm.uiState.value.message!!.contains("API Key"))
    }
}
