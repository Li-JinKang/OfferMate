package com.jk.offermate.agent.mcp

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class HttpMcpClientTest {

    private lateinit var server: MockWebServer

    /** 记录每个请求的 (body, session header)，供断言。 */
    private data class Captured(val body: String, val session: String?)

    private val captured = mutableListOf<Captured>()

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun client() = HttpMcpClient(
        serverUrl = server.url("/mcp").toString(),
        client = OkHttpClient()
    )

    /** 按请求内容路由响应（与请求顺序无关），并记录请求。 */
    private fun dispatch(responder: (method: String) -> MockResponse) {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val body = request.body.readUtf8()
                captured += Captured(body, request.getHeader("Mcp-Session-Id"))
                val method = Regex(""""method"\s*:\s*"([^"]+)"""").find(body)?.groupValues?.get(1).orEmpty()
                if (method.startsWith("notifications/")) return MockResponse().setResponseCode(202)
                return responder(method)
            }
        }
    }

    @Test
    fun `initializes then lists tools`() = runBlocking {
        dispatch { method ->
            when (method) {
                "initialize" -> MockResponse().setResponseCode(200)
                    .setHeader("Mcp-Session-Id", "sess-1")
                    .setBody("""{"jsonrpc":"2.0","id":1,"result":{"protocolVersion":"2025-06-18","capabilities":{}}}""")
                "tools/list" -> MockResponse().setResponseCode(200).setBody(
                    """{"jsonrpc":"2.0","id":2,"result":{"tools":[
                       {"name":"get_weather","description":"查天气","inputSchema":{"type":"object","properties":{"city":{"type":"string"}}}}
                    ]}}"""
                )
                else -> MockResponse().setResponseCode(404)
            }
        }

        val tools = client().listTools()

        assertEquals(1, tools.size)
        assertEquals("get_weather", tools[0].name)
        assertEquals("查天气", tools[0].description)
        assertTrue(tools[0].inputSchemaJson.contains("city"))
        assertTrue(captured.any { it.body.contains("initialize") })
    }

    @Test
    fun `calls tool and extracts text reusing session id`() = runBlocking {
        dispatch { method ->
            when (method) {
                "initialize" -> MockResponse().setResponseCode(200)
                    .setHeader("Mcp-Session-Id", "sess-9")
                    .setBody("""{"jsonrpc":"2.0","id":1,"result":{}}""")
                "tools/call" -> MockResponse().setResponseCode(200).setBody(
                    """{"jsonrpc":"2.0","id":2,"result":{"content":[{"type":"text","text":"晴，26℃"}]}}"""
                )
                else -> MockResponse().setResponseCode(404)
            }
        }

        val out = client().callTool("get_weather", """{"city":"北京"}""")
        assertEquals("晴，26℃", out)

        val callReq = captured.first { it.body.contains("tools/call") }
        assertEquals("sess-9", callReq.session)
        assertTrue(callReq.body.contains("北京"))
    }

    @Test
    fun `json-rpc error throws McpException`() {
        dispatch { method ->
            when (method) {
                "initialize" -> MockResponse().setResponseCode(200).setBody("""{"jsonrpc":"2.0","id":1,"result":{}}""")
                "tools/list" -> MockResponse().setResponseCode(200).setBody(
                    """{"jsonrpc":"2.0","id":2,"error":{"code":-32601,"message":"method not found"}}"""
                )
                else -> MockResponse().setResponseCode(404)
            }
        }
        assertThrows(McpException::class.java) {
            runBlocking { client().listTools() }
        }
    }
}
