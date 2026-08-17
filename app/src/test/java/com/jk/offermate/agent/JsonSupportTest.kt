package com.jk.offermate.agent

import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class JsonSupportTest {

    @Test
    fun `extracts json fence content`() {
        val raw = "前言\n```json\n[{\"index\":0}]\n```\n结尾"
        val block = JsonSupport.extractJsonBlock(raw)
        assertEquals("[{\"index\":0}]", block)
    }

    @Test
    fun `answer containing java code block does not hijack extraction`() {
        // 复现线上问题：answer 里内嵌 ```java 代码块（含 {}、转义换行）
        val raw = """
            [{"index":0,"answer":"单例：\n```java\npublic class Singleton {\n    private static volatile Singleton instance;\n    public static Singleton getInstance() {\n        if (instance == null) { synchronized (Singleton.class) {} }\n        return instance;\n    }\n}\n```","difficulty":"medium","keyPoints":["线程安全"]}]
        """.trimIndent()

        val block = JsonSupport.extractJsonBlock(raw)
        assertNotNull(block)
        val arr = JsonSupport.json.parseToJsonElement(block!!).jsonArray
        assertEquals(1, arr.size)
        val answer = arr[0].jsonObject["answer"]!!.jsonPrimitive.content
        assertTrue(answer.contains("volatile Singleton"))
        assertEquals("线程安全", arr[0].jsonObject["keyPoints"]!!.jsonArray[0].jsonPrimitive.content)
    }

    @Test
    fun `json fence truncated by inner code fence falls back to raw scan`() {
        val raw = "```json\n[{\"answer\":\"x:\\n```java\\nclass X{}\\n```\"}]\n```"
        val block = JsonSupport.extractJsonBlock(raw)
        assertNotNull(block)
        val arr = JsonSupport.json.parseToJsonElement(block!!).jsonArray
        assertEquals(1, arr.size)
    }

    @Test
    fun `object with results array`() {
        val raw = "```json\n{\"results\":[{\"index\":1}]}\n```"
        val block = JsonSupport.extractJsonBlock(raw)
        assertNotNull(block)
        assertTrue(JsonSupport.json.parseToJsonElement(block!!).jsonObject.containsKey("results"))
    }

    @Test
    fun `brackets inside string values do not truncate`() {
        val raw = """[{"answer":"数组用 arr[0] 访问，块用 {}"}]"""
        val block = JsonSupport.extractJsonBlock(raw)
        assertNotNull(block)
        val answer = JsonSupport.json.parseToJsonElement(block!!).jsonArray[0]
            .jsonObject["answer"]!!.jsonPrimitive.content
        assertTrue(answer.contains("arr[0]"))
    }

    @Test
    fun `returns null when no json present`() {
        assertEquals(null, JsonSupport.extractJsonBlock("这是一段没有 JSON 的文字"))
    }
}
