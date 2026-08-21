package com.jk.offermate.agent.mcp

import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** MCP 传输/协议解析纯函数测试。 */
class McpParsingTest {

    @Test
    fun `extract payload from direct json`() {
        val obj = extractJsonRpcPayload("""{"jsonrpc":"2.0","id":1,"result":{"tools":[]}}""")
        assertTrue(obj!!.containsKey("result"))
    }

    @Test
    fun `extract payload from SSE data lines`() {
        val sse = """
            event: message
            data: {"jsonrpc":"2.0","id":1,"result":{"ok":true}}

        """.trimIndent()
        val obj = extractJsonRpcPayload(sse)
        assertTrue(obj!!.containsKey("result"))
    }

    @Test
    fun `blank body yields null`() {
        assertNull(extractJsonRpcPayload("   "))
    }

    @Test
    fun `extract tool text joins text content blocks`() {
        val result = kotlinx.serialization.json.Json.parseToJsonElement(
            """{"content":[{"type":"text","text":"第一段"},{"type":"text","text":"第二段"}]}"""
        ).jsonObject
        assertEquals("第一段\n第二段", extractToolText(result))
    }

    @Test
    fun `extract tool text marks error`() {
        val result = kotlinx.serialization.json.Json.parseToJsonElement(
            """{"isError":true,"content":[{"type":"text","text":"boom"}]}"""
        ).jsonObject
        assertTrue(extractToolText(result).contains("错误"))
        assertTrue(extractToolText(result).contains("boom"))
    }

    @Test
    fun `sanitize tool name replaces illegal chars and truncates`() {
        assertEquals("mcp_srv_get-data", sanitizeToolName("mcp_srv_get-data"))
        assertEquals("a_b_c", sanitizeToolName("a.b/c"))
        assertEquals(64, sanitizeToolName("x".repeat(100)).length)
    }

    @Test
    fun `parse arguments falls back to empty object`() {
        assertTrue(parseArguments("not json").isEmpty())
        val parsed = parseArguments("""{"k":"v"}""")
        assertEquals("v", (parsed["k"] as kotlinx.serialization.json.JsonPrimitive).content)
    }
}
