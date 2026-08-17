package com.jk.offermate.agent

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ResumeReaderToolTest {

    private val resume = """
        目标岗位：Android 开发
        技能：Kotlin、Jetpack Compose、协程
        项目：即时通讯 App，负责消息同步与性能优化
    """.trimIndent()

    @Test
    fun `no query returns full resume`() = runTest {
        val tool = ResumeReaderTool(resumeTextProvider = { resume })
        val out = tool.call("{}")
        assertTrue(out.contains("目标岗位"))
        assertTrue(out.contains("即时通讯"))
    }

    @Test
    fun `query filters to matching lines`() = runTest {
        val tool = ResumeReaderTool(resumeTextProvider = { resume })
        val out = tool.call("""{"query":"技能"}""")
        assertTrue(out.contains("Kotlin"))
        assertFalse(out.contains("即时通讯"))
    }

    @Test
    fun `empty resume is reported`() = runTest {
        val tool = ResumeReaderTool(resumeTextProvider = { "" })
        assertEquals("（候选人尚未上传简历）", tool.call("{}"))
    }

    @Test
    fun `has expected spec`() {
        val tool = ResumeReaderTool(resumeTextProvider = { "" })
        assertEquals("read_resume", tool.spec.name)
    }
}
