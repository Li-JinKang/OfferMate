package com.jk.offermate.ui.home

import com.jk.offermate.agent.ResumeProfile
import com.jk.offermate.data.repository.FakePostRepository
import com.jk.offermate.data.resume.ResumeRepository
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

    private fun viewModel(
        scheduler: FakeImportScheduler = FakeImportScheduler(),
        resumeText: String = "我的简历内容"
    ) = HomeViewModel(
        postRepository = FakePostRepository(),
        importScheduler = scheduler,
        resumeRepository = FakeResumeRepository(ResumeProfile(targetRole = "", rawText = resumeText))
    )

    @Test
    fun `extract without resume shows hint and does not enqueue`() {
        val scheduler = FakeImportScheduler()
        val vm = viewModel(scheduler, resumeText = "")
        vm.onLinkChange("https://www.nowcoder.com/x")

        vm.onExtract()

        assertTrue(scheduler.urls.isEmpty())
        assertTrue(vm.uiState.value.message!!.contains("简历"))
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
    fun `paste analyze enqueues text`() {
        val scheduler = FakeImportScheduler()
        val vm = viewModel(scheduler)

        vm.onPasteAnalyze("一段粘贴的正文")

        assertEquals(listOf("一段粘贴的正文"), scheduler.texts)
    }
}
