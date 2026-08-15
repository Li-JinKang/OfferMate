package com.jk.offermate.ui.home

import com.jk.offermate.data.ai.AnsweredQuestion
import com.jk.offermate.data.ai.ResumeProfile
import com.jk.offermate.data.importer.ImportResult
import com.jk.offermate.data.importer.Importer
import com.jk.offermate.data.reader.ExtractionMethod
import com.jk.offermate.data.reader.PostContent
import com.jk.offermate.data.repository.FakePostRepository
import com.jk.offermate.data.resume.ResumeRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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

    private class FakeImporter(var result: ImportResult) : Importer {
        override suspend fun importFromUrl(url: String, profile: ResumeProfile) = result
        override suspend fun importFromText(text: String, profile: ResumeProfile, sourceUrl: String) = result
    }

    private class FakeResumeRepository(profile: ResumeProfile) : ResumeRepository {
        private val state = MutableStateFlow(profile)
        override val profile: Flow<ResumeProfile> = state
        override suspend fun save(targetRole: String, skillsCsv: String, rawText: String) {
            state.value = ResumeProfile(targetRole, ResumeRepository.parseSkills(skillsCsv), rawText = rawText)
        }
    }

    private fun viewModel(
        importResult: ImportResult,
        targetRole: String = "Android 开发"
    ) = HomeViewModel(
        postRepository = FakePostRepository(),
        importer = FakeImporter(importResult),
        resumeRepository = FakeResumeRepository(ResumeProfile(targetRole = targetRole))
    )

    private val sampleSuccess = ImportResult.Success(
        content = PostContent("", "正文", "u", ExtractionMethod.MANUAL),
        questions = listOf(AnsweredQuestion(question = "Q1", answer = "A1", relevanceScore = 90))
    )

    @Test
    fun `extract without resume shows hint`() {
        val vm = viewModel(sampleSuccess, targetRole = "")
        vm.onLinkChange("https://www.nowcoder.com/x")

        vm.onExtract()

        assertTrue(vm.uiState.value.results.isEmpty())
        assertTrue(vm.uiState.value.message!!.contains("目标岗位"))
    }

    @Test
    fun `extract success populates results`() {
        val vm = viewModel(sampleSuccess)
        vm.onLinkChange("https://www.nowcoder.com/x")

        vm.onExtract()

        val state = vm.uiState.value
        assertEquals(1, state.results.size)
        assertEquals("Q1", state.results[0].question)
        assertNull(state.message)
        assertTrue(!state.isExtracting)
    }

    @Test
    fun `extract failure shows reason`() {
        val vm = viewModel(ImportResult.Failed("未配置 Key"))
        vm.onLinkChange("https://www.nowcoder.com/x")

        vm.onExtract()

        assertEquals("未配置 Key", vm.uiState.value.message)
    }

    @Test
    fun `read failure shows manual paste`() {
        val vm = viewModel(ImportResult.NeedsManualInput("https://real", "读取失败"))
        vm.onLinkChange("https://xhslink.cn/x")

        vm.onExtract()

        assertTrue(vm.uiState.value.manualPasteVisible)
    }
}
