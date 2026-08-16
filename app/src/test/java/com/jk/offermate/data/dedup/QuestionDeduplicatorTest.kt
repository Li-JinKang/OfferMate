package com.jk.offermate.data.dedup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QuestionDeduplicatorTest {

    private val dedup = QuestionDeduplicator(hammingThreshold = 3)

    // --- normalize ---

    @Test
    fun `normalize strips punctuation and whitespace and lowercases`() {
        assertEquals("whatiskotlin", dedup.normalize("What is Kotlin?  "))
    }

    @Test
    fun `normalize folds fullwidth to halfwidth`() {
        assertEquals("abc123", dedup.normalize("ＡＢＣ１２３"))
    }

    @Test
    fun `normalize keeps cjk drops symbols`() {
        assertEquals("协程原理", dedup.normalize("协程（原理）!"))
    }

    // --- bucketKey ---

    @Test
    fun `bucket key uses first tag lowercased`() {
        assertEquals("kotlin", dedup.bucketKey(listOf("Kotlin", "并发")))
    }

    @Test
    fun `bucket key falls back to notag bucket`() {
        assertEquals(QuestionDeduplicator.NO_TAG_BUCKET, dedup.bucketKey(emptyList()))
        assertEquals(QuestionDeduplicator.NO_TAG_BUCKET, dedup.bucketKey(listOf("  ")))
    }

    // --- fingerprint / exact ---

    @Test
    fun `fingerprint is deterministic`() {
        val a = dedup.fingerprint("谈谈 Kotlin 协程", listOf("Kotlin"))
        val b = dedup.fingerprint("谈谈 Kotlin 协程", listOf("Kotlin"))
        assertEquals(a, b)
    }

    @Test
    fun `text differing only by punctuation is an exact duplicate`() {
        val a = dedup.fingerprint("什么是协程？", listOf("并发"))
        val b = dedup.fingerprint("什么是协程", listOf("并发"))
        assertEquals(a.exactHash, b.exactHash)
        assertEquals(0, dedup.hamming(a.simhash, b.simhash))
        assertTrue(dedup.isDuplicate(a, b))
    }

    // --- hamming ---

    @Test
    fun `hamming counts differing bits`() {
        assertEquals(0, dedup.hamming(0b1011L, 0b1011L))
        assertEquals(3, dedup.hamming(0b000L, 0b111L))
    }

    // --- isDuplicate threshold + bucket gating (deterministic, hand-crafted) ---

    @Test
    fun `near duplicate within threshold and same bucket is duplicate`() {
        val a = QuestionFingerprint("hashA", 0b000L, "kotlin")
        val b = QuestionFingerprint("hashB", 0b111L, "kotlin") // 3 bits → within threshold
        assertTrue(dedup.isDuplicate(a, b))
    }

    @Test
    fun `beyond threshold is not duplicate`() {
        val a = QuestionFingerprint("hashA", 0b0000L, "kotlin")
        val c = QuestionFingerprint("hashC", 0b1111L, "kotlin") // 4 bits → beyond threshold
        assertFalse(dedup.isDuplicate(a, c))
    }

    @Test
    fun `different bucket blocks near duplicate but not exact`() {
        val a = QuestionFingerprint("hashA", 0b000L, "kotlin")
        val nearOtherBucket = QuestionFingerprint("hashB", 0b111L, "java")
        assertFalse(dedup.isDuplicate(a, nearOtherBucket))

        val exactOtherBucket = QuestionFingerprint("same", 42L, "java")
        val exactHere = QuestionFingerprint("same", 99L, "kotlin")
        assertTrue(dedup.isDuplicate(exactHere, exactOtherBucket))
    }

    @Test
    fun `empty fingerprints are never duplicates`() {
        val a = QuestionFingerprint("", 0L, "kotlin")
        val b = QuestionFingerprint("", 0L, "kotlin")
        assertFalse(dedup.isDuplicate(a, b))
    }
}
