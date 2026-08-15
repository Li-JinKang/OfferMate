package com.jk.offermate.data.resume

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ResumeSkillsParseTest {

    @Test
    fun `splits by comma ideographic comma and newline`() {
        val skills = ResumeRepository.parseSkills("Android，Kotlin, JVM、协程\nRoom")
        assertEquals(listOf("Android", "Kotlin", "JVM", "协程", "Room"), skills)
    }

    @Test
    fun `trims and drops blanks`() {
        val skills = ResumeRepository.parseSkills("  Android ,, ，  Kotlin  ")
        assertEquals(listOf("Android", "Kotlin"), skills)
    }

    @Test
    fun `empty input yields empty list`() {
        assertTrue(ResumeRepository.parseSkills("   ").isEmpty())
    }
}
