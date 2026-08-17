package com.jk.offermate.agent

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class DeepSeekToolCallingTest {

    private lateinit var server: MockWebServer

    @Before fun setUp() { server = MockWebServer(); server.start() }
    @After fun tearDown() { server.shutdown() }

    private fun client() = DeepSeekClient(
        apiKeyProvider = { "sk-test" },
        modelProvider = { "deepseek-chat" },
        baseUrlProvider = { server.url("/").toString() },
        client = OkHttpClient()
    )

    private val readResume = ToolSpec(
        name = "read_resume",
        description = "读取简历",
        parametersJson = """{"type":"object","properties":{"query":{"type":"string"}}}"""
    )

    @Test
    fun `parses tool_calls turn and sends tools in request`() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"choices":[{"message":{"role":"assistant","content":null,
                   "tool_calls":[{"id":"call_1","type":"function",
                   "function":{"name":"read_resume","arguments":"{\"query\":\"kotlin\"}"}}]}}]}"""
            )
        )

        val turn = client().chat(listOf(ChatMessage(Role.USER, "结合简历回答")), listOf(readResume))

        assertTrue(turn is LlmTurn.ToolInvocations)
        val call = (turn as LlmTurn.ToolInvocations).calls.single()
        assertEquals("read_resume", call.name)
        assertEquals("call_1", call.id)
        assertTrue(call.argumentsJson.contains("kotlin"))

        val body = server.takeRequest().body.readUtf8()
        assertTrue(body.contains("\"tools\""))
        assertTrue(body.contains("read_resume"))
    }

    @Test
    fun `parses final text turn`() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"choices":[{"message":{"role":"assistant","content":"最终答案"}}]}"""
            )
        )

        val turn = client().chat(listOf(ChatMessage(Role.USER, "q")), listOf(readResume))

        assertTrue(turn is LlmTurn.Final)
        assertEquals("最终答案", (turn as LlmTurn.Final).text)
    }

    @Test
    fun `serializes tool result message with tool_call_id`() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"choices":[{"message":{"role":"assistant","content":"ok"}}]}"""
            )
        )

        client().chat(
            listOf(
                ChatMessage(Role.USER, "q"),
                ChatMessage(Role.ASSISTANT, "", toolCalls = listOf(ToolCall("call_1", "read_resume", "{}"))),
                ChatMessage(Role.TOOL, "简历文本", toolCallId = "call_1", toolName = "read_resume")
            ),
            emptyList()
        )

        val body = server.takeRequest().body.readUtf8()
        assertTrue(body.contains("\"role\":\"tool\""))
        assertTrue(body.contains("\"tool_call_id\":\"call_1\""))
        assertTrue(body.contains("\"tool_calls\""))
    }
}
