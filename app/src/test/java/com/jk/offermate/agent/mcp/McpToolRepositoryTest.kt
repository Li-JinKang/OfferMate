package com.jk.offermate.agent.mcp

import com.jk.offermate.agent.tool.ToolRegistry
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class McpToolRepositoryTest {

    private class FakeClient(
        private val tools: List<McpToolSpec>,
        private val failList: Boolean = false
    ) : McpClient {
        var lastCall: Pair<String, String>? = null
        override suspend fun listTools(): List<McpToolSpec> {
            if (failList) throw McpException("connect failed")
            return tools
        }
        override suspend fun callTool(name: String, argumentsJson: String): String {
            lastCall = name to argumentsJson
            return "result:$name"
        }
    }

    @Test
    fun `discovers and namespaces tools`() = runTest {
        val server = McpServerConfig("Weather", "https://x/mcp")
        val client = FakeClient(listOf(McpToolSpec("get_weather", "查天气", """{"type":"object"}""")))
        val repo = McpToolRepository(
            serversProvider = { listOf(server) },
            clientFactory = { client }
        )

        val count = repo.refresh()

        assertEquals(1, count)
        val tool = repo.current().single()
        assertEquals("mcp_Weather_get_weather", tool.spec.name)
        // 调用时转发到原始工具名
        assertEquals("result:get_weather", tool.call("{}"))
        assertEquals("get_weather", client.lastCall?.first)
    }

    @Test
    fun `disabled servers are skipped`() = runTest {
        val repo = McpToolRepository(
            serversProvider = { listOf(McpServerConfig("A", "https://x", enabled = false)) },
            clientFactory = { FakeClient(listOf(McpToolSpec("t", "d", ""))) }
        )
        assertEquals(0, repo.refresh())
    }

    @Test
    fun `failing server is skipped, others still discovered`() = runTest {
        val ok = McpServerConfig("Ok", "https://ok")
        val bad = McpServerConfig("Bad", "https://bad")
        val repo = McpToolRepository(
            serversProvider = { listOf(bad, ok) },
            clientFactory = { cfg ->
                if (cfg.name == "Bad") FakeClient(emptyList(), failList = true)
                else FakeClient(listOf(McpToolSpec("ping", "d", "")))
            }
        )
        assertEquals(1, repo.refresh())
        assertEquals("mcp_Ok_ping", repo.current().single().spec.name)
    }

    @Test
    fun `name collisions get unique suffixes`() = runTest {
        val repo = McpToolRepository(
            serversProvider = { listOf(McpServerConfig("S", "https://x")) },
            clientFactory = {
                FakeClient(listOf(McpToolSpec("t", "d", ""), McpToolSpec("t", "d2", "")))
            }
        )
        repo.refresh()
        val names = repo.current().map { it.spec.name }
        assertEquals(2, names.toSet().size)
    }

    @Test
    fun `dynamic ToolRegistry reflects discovered tools`() = runTest {
        val repo = McpToolRepository(
            serversProvider = { listOf(McpServerConfig("S", "https://x")) },
            clientFactory = { FakeClient(listOf(McpToolSpec("t", "d", ""))) }
        )
        val registry = ToolRegistry { repo.current() }

        assertTrue(registry.isEmpty())          // 刷新前为空
        repo.refresh()
        assertEquals(1, registry.specs().size)  // 刷新后自动可见
    }
}
