package com.jk.offermate.data.memory

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

class MemoryStoreTest {

    private lateinit var root: File
    private lateinit var store: MemoryStore

    @Before
    fun setUp() {
        root = Files.createTempDirectory("memtest").toFile()
        store = MemoryStore(root)
    }

    @Test
    fun `empty store returns empty index and null files`() = runTest {
        assertTrue(store.listProfiles().isEmpty())
        assertNull(store.readProfileOverview("java-backend"))
        assertNull(store.readGlobal())
    }

    @Test
    fun `upsert creates folder and index entry`() = runTest {
        val entry = MemoryProfileEntry("java-backend", "Java 后端", "Java 后端开发", "3 年经验")
        store.upsertProfile(entry)

        assertEquals(listOf(entry), store.listProfiles())
        assertTrue(File(root, "java-backend").isDirectory)
    }

    @Test
    fun `upsert with same id replaces entry in place`() = runTest {
        store.upsertProfile(MemoryProfileEntry("p1", "旧名", "旧岗位"))
        store.upsertProfile(MemoryProfileEntry("p2", "Android", "Android 开发"))
        store.upsertProfile(MemoryProfileEntry("p1", "新名", "新岗位", "更新后的简述"))

        val profiles = store.listProfiles()
        assertEquals(2, profiles.size)
        // 顺序保持，p1 仍在首位且被替换
        assertEquals("新名", profiles[0].name)
        assertEquals("新岗位", profiles[0].targetRole)
        assertEquals("更新后的简述", profiles[0].summary)
        assertEquals("p2", profiles[1].id)
    }

    @Test
    fun `overview read write roundtrip`() = runTest {
        store.writeProfileOverview("java-backend", "# 技能\n- Spring\n- MySQL")
        assertEquals("# 技能\n- Spring\n- MySQL", store.readProfileOverview("java-backend"))
    }

    @Test
    fun `detail read write and listing per kind`() = runTest {
        store.writeDetail("jb", DetailKind.PROJECT, "order-system", "订单系统细节")
        store.writeDetail("jb", DetailKind.PROJECT, "im-gateway", "IM 网关细节")
        store.writeDetail("jb", DetailKind.EXPERIENCE, "company-a", "在 A 公司的经历")

        assertEquals("订单系统细节", store.readDetail("jb", DetailKind.PROJECT, "order-system"))
        assertEquals(listOf("im-gateway", "order-system"), store.listDetails("jb", DetailKind.PROJECT))
        assertEquals(listOf("company-a"), store.listDetails("jb", DetailKind.EXPERIENCE))
        assertNull(store.readDetail("jb", DetailKind.PROJECT, "missing"))
    }

    @Test
    fun `deleteDetail removes only that file`() = runTest {
        store.writeDetail("jb", DetailKind.PROJECT, "a", "A")
        store.writeDetail("jb", DetailKind.PROJECT, "b", "B")

        assertTrue(store.deleteDetail("jb", DetailKind.PROJECT, "a"))
        assertEquals(listOf("b"), store.listDetails("jb", DetailKind.PROJECT))
        assertNull(store.readDetail("jb", DetailKind.PROJECT, "a"))
    }

    @Test
    fun `multiple profiles coexist without interference`() = runTest {
        store.upsertProfile(MemoryProfileEntry("jb", "Java 后端", "Java 后端"))
        store.upsertProfile(MemoryProfileEntry("ad", "Android", "Android"))
        store.writeProfileOverview("jb", "后端概览")
        store.writeProfileOverview("ad", "安卓概览")
        store.writeDetail("jb", DetailKind.PROJECT, "p", "后端项目")

        assertEquals("后端概览", store.readProfileOverview("jb"))
        assertEquals("安卓概览", store.readProfileOverview("ad"))
        // ad 没有 project，互不干扰
        assertTrue(store.listDetails("ad", DetailKind.PROJECT).isEmpty())
        assertEquals(2, store.listProfiles().size)
    }

    @Test
    fun `global read write roundtrip`() = runTest {
        store.writeGlobal("工作年限: 3 年\n学历: 本科")
        assertEquals("工作年限: 3 年\n学历: 本科", store.readGlobal())
    }

    @Test
    fun `removeProfile deletes folder and index entry`() = runTest {
        store.upsertProfile(MemoryProfileEntry("jb", "Java 后端", "Java 后端"))
        store.writeProfileOverview("jb", "概览")
        store.upsertProfile(MemoryProfileEntry("ad", "Android", "Android"))

        store.removeProfile("jb")

        assertEquals(listOf("ad"), store.listProfiles().map { it.id })
        assertTrue(!File(root, "jb").exists())
    }

    @Test
    fun `clearAll removes everything`() = runTest {
        store.upsertProfile(MemoryProfileEntry("jb", "Java 后端", "Java 后端"))
        store.writeGlobal("global")
        store.clearAll()

        assertTrue(!root.exists())
        assertTrue(store.listProfiles().isEmpty())
        assertNull(store.readGlobal())
    }

    @Test
    fun `illegal ids are rejected to prevent path traversal`() = runTest {
        assertThrows { store.writeProfileOverview("../evil", "x") }
        assertThrows { store.readDetail("jb", DetailKind.PROJECT, "../../etc/passwd") }
        assertThrows { store.upsertProfile(MemoryProfileEntry("a/b", "n", "r")) }
    }

    private inline fun assertThrows(block: () -> Unit) {
        try {
            block()
            throw AssertionError("expected exception was not thrown")
        } catch (e: IllegalArgumentException) {
            // expected
        }
    }
}
