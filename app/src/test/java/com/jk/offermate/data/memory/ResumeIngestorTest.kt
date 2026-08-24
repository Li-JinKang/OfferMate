package com.jk.offermate.data.memory

import com.jk.offermate.agent.FakeAiClient
import com.jk.offermate.agent.resume.ProfileMatcher
import com.jk.offermate.agent.resume.ResumeStructurer
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

class ResumeIngestorTest {

    private lateinit var root: File
    private lateinit var store: MemoryStore

    @Before
    fun setUp() {
        root = Files.createTempDirectory("ingest").toFile()
        store = MemoryStore(root)
    }

    private fun ingestorReturning(structuredJson: String, matchJson: String): ResumeIngestor {
        val ai = FakeAiClient { msgs ->
            val sys = msgs.firstOrNull()?.content.orEmpty()
            when {
                sys.contains("简历结构化助手") -> structuredJson
                sys.contains("求职方向") -> matchJson
                else -> "{}"
            }
        }
        return ResumeIngestor(ResumeStructurer(ai), ProfileMatcher(ai), store)
    }

    private val javaResume = """
        {"id":"java-backend","name":"Java 后端","targetRole":"Java 后端开发",
         "summary":"3 年后端","skills":["Java","Spring"],
         "globalFacts":["工作年限: 3 年"],
         "projects":[{"id":"order-system","title":"订单系统","brief":"高并发","detail":"RocketMQ 削峰"}]}
    """.trimIndent()

    private val androidResume = """
        {"id":"android","name":"Android","targetRole":"Android 开发",
         "summary":"移动端","skills":["Kotlin","Compose"],
         "projects":[{"id":"chat-app","title":"IM App","brief":"即时通讯","detail":"Compose UI"}]}
    """.trimIndent()

    @Test
    fun `new direction on empty store creates profile and files`() = runTest {
        val result = ingestorReturning(javaResume, """{"matchedId":null}""").ingest("原文")

        assertTrue(result.isNew)
        assertEquals("java-backend", result.profileId)
        assertEquals(listOf("java-backend"), store.listProfiles().map { it.id })
        assertNotNull(store.readProfileOverview("java-backend"))
        assertTrue(store.readProfileOverview("java-backend")!!.contains("Spring"))
        assertEquals(listOf("order-system"), store.listDetails("java-backend", DetailKind.PROJECT))
        assertTrue(store.readDetail("java-backend", DetailKind.PROJECT, "order-system")!!.contains("RocketMQ"))
        assertTrue(store.readGlobal()!!.contains("工作年限"))
    }

    @Test
    fun `same direction match overwrites in place`() = runTest {
        // 先放一份旧的 java-backend
        store.upsertProfile(MemoryProfileEntry("java-backend", "Java 后端", "Java 后端开发", "旧简述"))
        store.writeProfileOverview("java-backend", "旧概览")

        val result = ingestorReturning(javaResume, """{"matchedId":"java-backend"}""").ingest("新原文")

        assertFalse(result.isNew)
        assertEquals("java-backend", result.profileId)
        // 仍只有一份，概览被覆盖为新内容，index summary 更新
        assertEquals(1, store.listProfiles().size)
        assertEquals("3 年后端", store.listProfiles()[0].summary)
        assertTrue(store.readProfileOverview("java-backend")!!.contains("Spring"))
        assertFalse(store.readProfileOverview("java-backend")!!.contains("旧概览"))
    }

    @Test
    fun `new direction coexists with old without touching it`() = runTest {
        store.upsertProfile(MemoryProfileEntry("java-backend", "Java 后端", "Java 后端开发", "老方向"))
        store.writeProfileOverview("java-backend", "后端概览")

        val result = ingestorReturning(androidResume, """{"matchedId":null}""").ingest("安卓简历")

        assertTrue(result.isNew)
        assertEquals("android", result.profileId)
        // 两份并存，旧的完好无损
        assertEquals(setOf("java-backend", "android"), store.listProfiles().map { it.id }.toSet())
        assertEquals("后端概览", store.readProfileOverview("java-backend"))
        assertTrue(store.readProfileOverview("android")!!.contains("Compose"))
    }

    @Test
    fun `new id is uniquified when slug collides with a different direction`() = runTest {
        // 已存在 id=android，但匹配判定为"新建"（不同方向），需避免 id 冲突
        store.upsertProfile(MemoryProfileEntry("android", "占位", "别的方向"))

        val result = ingestorReturning(androidResume, """{"matchedId":null}""").ingest("原文")

        assertTrue(result.isNew)
        assertEquals("android-2", result.profileId)
        assertEquals(2, store.listProfiles().size)
    }
}
