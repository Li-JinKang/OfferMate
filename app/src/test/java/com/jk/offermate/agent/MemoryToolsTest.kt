package com.jk.offermate.agent

import com.jk.offermate.data.memory.DetailKind
import com.jk.offermate.data.memory.MemoryProfileEntry
import com.jk.offermate.data.memory.MemoryStore
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

class MemoryToolsTest {

    private lateinit var store: MemoryStore

    @Before
    fun setUp() {
        val root: File = Files.createTempDirectory("memtools").toFile()
        store = MemoryStore(root)
    }

    private suspend fun seed() {
        store.upsertProfile(MemoryProfileEntry("java-backend", "Java 后端", "Java 后端开发", "3 年经验"))
        store.upsertProfile(MemoryProfileEntry("android", "Android", "Android 开发"))
        store.writeProfileOverview("java-backend", "# Java 后端\n## 技能\n- Spring\n- 分布式\n## 项目\n- 订单系统：高并发")
        store.writeProfileOverview("android", "# Android\n## 技能\n- Compose")
        store.writeGlobal("# 通用\n- 工作年限: 3 年")
        store.writeDetail("java-backend", DetailKind.PROJECT, "order-system", "订单系统：用 RocketMQ 削峰填谷")
        store.writeDetail("java-backend", DetailKind.EXPERIENCE, "company-a", "在 A 公司负责交易链路")
    }

    // ---- L1 ----

    @Test
    fun `list profiles returns all entries`() = runTest {
        seed()
        val out = ListMemoryProfilesTool(store).call("{}")
        assertTrue(out.contains("java-backend"))
        assertTrue(out.contains("Android"))
        assertTrue(out.contains("3 年经验"))
    }

    @Test
    fun `list profiles empty message`() = runTest {
        val out = ListMemoryProfilesTool(store).call("{}")
        assertTrue(out.contains("尚无"))
    }

    // ---- L2 ----

    @Test
    fun `overview includes profile and global`() = runTest {
        seed()
        val out = LoadProfileOverviewTool(store).call("""{"profileId":"java-backend"}""")
        assertTrue(out.contains("Spring"))
        assertTrue(out.contains("工作年限: 3 年")) // global 拼接进来
    }

    @Test
    fun `overview query filters lines`() = runTest {
        seed()
        val out = LoadProfileOverviewTool(store).call("""{"profileId":"java-backend","query":"分布式"}""")
        assertTrue(out.contains("分布式"))
        assertFalse(out.contains("Spring"))
    }

    @Test
    fun `overview missing profile message`() = runTest {
        seed()
        val out = LoadProfileOverviewTool(store).call("""{"profileId":"golang"}""")
        assertTrue(out.contains("未找到"))
    }

    @Test
    fun `overview missing param message`() = runTest {
        val out = LoadProfileOverviewTool(store).call("{}")
        assertTrue(out.contains("缺少参数"))
    }

    // ---- L3 ----

    @Test
    fun `project detail returns full text`() = runTest {
        seed()
        val out = LoadProjectDetailTool(store).call(
            """{"profileId":"java-backend","projectId":"order-system"}"""
        )
        assertTrue(out.contains("RocketMQ"))
    }

    @Test
    fun `experience detail returns full text`() = runTest {
        seed()
        val out = LoadExperienceDetailTool(store).call(
            """{"profileId":"java-backend","experienceId":"company-a"}"""
        )
        assertTrue(out.contains("交易链路"))
    }

    @Test
    fun `detail missing item message`() = runTest {
        seed()
        val out = LoadProjectDetailTool(store).call(
            """{"profileId":"java-backend","projectId":"missing"}"""
        )
        assertTrue(out.contains("未找到"))
    }

    @Test
    fun `cross profile does not leak`() = runTest {
        seed()
        // android 下没有 order-system 项目
        val out = LoadProjectDetailTool(store).call(
            """{"profileId":"android","projectId":"order-system"}"""
        )
        assertTrue(out.contains("未找到"))
    }

    // ---- 工厂 & 注册 ----

    @Test
    fun `factory produces four tools with unique names`() {
        val tools = memoryTools(store)
        val names = tools.map { it.spec.name }
        assertEquals(4, tools.size)
        assertEquals(names.toSet().size, names.size)
        assertTrue(names.containsAll(
            listOf("list_memory_profiles", "load_profile_overview", "load_project_detail", "load_experience_detail")
        ))
    }

    @Test
    fun `tools resolvable via ToolRegistry`() = runTest {
        seed()
        val registry = ToolRegistry(memoryTools(store))
        val out = registry.find("list_memory_profiles")!!.call("{}")
        assertTrue(out.contains("java-backend"))
    }
}
