package com.jk.offermate.agent.resume

import com.jk.offermate.agent.AiException
import com.jk.offermate.agent.FakeAiClient
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ResumeStructurerTest {

    private val structurer = ResumeStructurer(FakeAiClient.returning(""))

    @Test
    fun `parses full structured resume`() {
        val raw = """
            ```json
            {
              "id": "java-backend",
              "name": "Java 后端",
              "targetRole": "Java 后端开发工程师",
              "summary": "3 年经验，擅长分布式",
              "skills": ["Java", "Spring", "MySQL"],
              "globalFacts": ["工作年限: 3 年", "学历: 本科"],
              "projects": [
                {"id":"order-system","title":"订单系统","brief":"高并发下单","detail":"用 RocketMQ 削峰..."}
              ],
              "experiences": [
                {"id":"company-a","title":"A公司-后端","brief":"核心交易","detail":"负责交易链路..."}
              ]
            }
            ```
        """.trimIndent()

        val s = ResumeStructurer(FakeAiClient.returning(raw)).parse(raw)

        assertEquals("java-backend", s.suggestedId)
        assertEquals("Java 后端", s.name)
        assertEquals("Java 后端开发工程师", s.targetRole)
        assertEquals("3 年经验，擅长分布式", s.summary)
        assertEquals(listOf("Java", "Spring", "MySQL"), s.skills)
        assertEquals(listOf("工作年限: 3 年", "学历: 本科"), s.globalFacts)
        assertEquals(1, s.projects.size)
        assertEquals("order-system", s.projects[0].id)
        assertEquals("高并发下单", s.projects[0].brief)
        assertEquals(1, s.experiences.size)
        assertEquals("company-a", s.experiences[0].id)
    }

    @Test
    fun `tolerates missing optional fields`() {
        val raw = """{"targetRole":"Android 开发","name":"Android"}"""
        val s = structurer.parse(raw)
        assertEquals("Android 开发", s.targetRole)
        assertTrue(s.skills.isEmpty())
        assertTrue(s.projects.isEmpty())
        assertTrue(s.globalFacts.isEmpty())
    }

    @Test
    fun `structure calls ai and parses response`() = runTest {
        val raw = """{"id":"android","name":"Android","targetRole":"Android 开发"}"""
        val ai = FakeAiClient.returning(raw)
        val s = ResumeStructurer(ai).structure("我的简历……")
        assertEquals("Android 开发", s.targetRole)
        // buildMessages 带上了简历原文
        assertTrue(ai.lastMessages.last().content.contains("我的简历"))
    }

    @Test
    fun `throws when no json`() {
        assertThrows(AiException::class.java) { structurer.parse("这里没有 JSON") }
    }
}
