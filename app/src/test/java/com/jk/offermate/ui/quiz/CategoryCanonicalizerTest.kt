package com.jk.offermate.ui.quiz

import com.jk.offermate.agent.AnsweredQuestion
import com.jk.offermate.agent.QuestionSource
import org.junit.Assert.assertEquals
import org.junit.Test

class CategoryCanonicalizerTest {

    @Test
    fun `android fine tags roll up to Android`() {
        assertEquals("Android", CategoryCanonicalizer.canonical(listOf("Handler")))
        assertEquals("Android", CategoryCanonicalizer.canonical(listOf("SharedPreferences")))
        assertEquals("Android", CategoryCanonicalizer.canonical(listOf("自定义View 绘制")))
    }

    @Test
    fun `kotlin and java are distinguished`() {
        assertEquals("Kotlin", CategoryCanonicalizer.canonical(listOf("协程")))
        assertEquals("Java", CategoryCanonicalizer.canonical(listOf("HashMap 扩容")))
    }

    @Test
    fun `other domains`() {
        assertEquals("计算机网络", CategoryCanonicalizer.canonical(listOf("TCP 三次握手")))
        assertEquals("操作系统", CategoryCanonicalizer.canonical(listOf("进程与线程")))
        assertEquals("数据结构与算法", CategoryCanonicalizer.canonical(listOf("动态规划")))
        assertEquals("数据库", CategoryCanonicalizer.canonical(listOf("MySQL 索引")))
        assertEquals("系统设计", CategoryCanonicalizer.canonical(listOf("单例模式")))
    }

    @Test
    fun `matches against question text when tag is generic`() {
        assertEquals(
            "Android",
            CategoryCanonicalizer.canonical(tags = listOf("基础"), question = "说说 Handler 的原理")
        )
    }

    @Test
    fun `unknown falls back to first tag then other`() {
        assertEquals("产品思维", CategoryCanonicalizer.canonical(listOf("产品思维")))
        assertEquals(CategoryCanonicalizer.OTHER, CategoryCanonicalizer.canonical(emptyList()))
    }

    @Test
    fun `manual question keeps user category without roll-up`() {
        val manual = AnsweredQuestion(
            question = "说说 Handler",
            answer = "",
            tags = listOf("我的收藏"),
            source = QuestionSource.MANUAL
        )
        assertEquals("我的收藏", CategoryCanonicalizer.categoryOf(manual))
    }

    @Test
    fun `ai question rolls up`() {
        val ai = AnsweredQuestion(question = "Handler 原理", answer = "", tags = listOf("Handler"))
        assertEquals("Android", CategoryCanonicalizer.categoryOf(ai))
    }
}
