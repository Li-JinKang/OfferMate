package com.jk.offermate.data.dedup

import java.text.Normalizer

/**
 * 题目指纹：
 * @param exactHash 规范化后的文本（精确重复判定，字符串相等即重复）。
 * @param simhash   64 位 SimHash（近似重复判定，按汉明距离阈值）。
 * @param bucketKey LSH 分桶键（当前按首个考点标签分区），只在同桶内做近似比对，避免全表扫描。
 */
data class QuestionFingerprint(
    val exactHash: String,
    val simhash: Long,
    val bucketKey: String
)

/**
 * 题目相似去重（纯逻辑，JVM 可测）。
 *
 * 设计目标：随导入增多，题库会积累语义重复的题目（同一问法的不同表述）。入库前做增量去重，
 * **不扫全表**：只与"同分桶"的候选比对。
 * - 精确重复：规范化指纹（去标点/空白、全半角统一、小写）相等 → O(1) 命中。
 * - 近似重复：SimHash + 汉明距离，命中阈值即视为重复。
 */
class QuestionDeduplicator(private val hammingThreshold: Int = 3) {

    /** 规范化：NFKC（全角→半角等）+ 小写 + 仅保留字母/数字/CJK（去标点与空白）。 */
    fun normalize(text: String): String {
        val nfkc = Normalizer.normalize(text, Normalizer.Form.NFKC).lowercase()
        return buildString(nfkc.length) {
            for (c in nfkc) if (Character.isLetterOrDigit(c)) append(c)
        }
    }

    /** 分桶键：首个考点标签（规范化）；无标签时归入统一无标签桶。 */
    fun bucketKey(tags: List<String>): String =
        tags.firstOrNull()?.trim()?.lowercase()?.takeIf { it.isNotEmpty() } ?: NO_TAG_BUCKET

    /** 为一道题计算指纹。 */
    fun fingerprint(question: String, tags: List<String> = emptyList()): QuestionFingerprint {
        val norm = normalize(question)
        return QuestionFingerprint(exactHash = norm, simhash = simhash(norm), bucketKey = bucketKey(tags))
    }

    /**
     * 计算 64 位 SimHash：以字符 bigram 作为特征（对中文更稳健），按特征频次加权。
     */
    fun simhash(normalized: String): Long {
        if (normalized.isEmpty()) return 0L
        val features = shingles(normalized)
        val weights = IntArray(64)
        for ((feature, count) in features) {
            val h = hash64(feature)
            for (bit in 0 until 64) {
                if ((h ushr bit) and 1L == 1L) weights[bit] += count else weights[bit] -= count
            }
        }
        var result = 0L
        for (bit in 0 until 64) {
            if (weights[bit] > 0) result = result or (1L shl bit)
        }
        return result
    }

    /** 两指纹的汉明距离（异或后置位数）。 */
    fun hamming(a: Long, b: Long): Int = java.lang.Long.bitCount(a xor b)

    /** 是否重复：精确相等，或同桶且 SimHash 汉明距离不超过阈值。 */
    fun isDuplicate(a: QuestionFingerprint, b: QuestionFingerprint): Boolean {
        if (a.exactHash.isEmpty() || b.exactHash.isEmpty()) return false
        if (a.exactHash == b.exactHash) return true
        return a.bucketKey == b.bucketKey && hamming(a.simhash, b.simhash) <= hammingThreshold
    }

    private fun shingles(s: String): Map<String, Int> {
        if (s.length == 1) return mapOf(s to 1)
        val map = HashMap<String, Int>()
        for (i in 0 until s.length - 1) {
            val gram = s.substring(i, i + 2)
            map[gram] = (map[gram] ?: 0) + 1
        }
        return map
    }

    /** FNV-1a 64 位哈希，保证跨平台确定性（不依赖 String.hashCode）。 */
    private fun hash64(s: CharSequence): Long {
        var h = FNV_OFFSET_BASIS
        for (c in s) {
            h = h xor c.code.toLong()
            h *= FNV_PRIME
        }
        return h
    }

    companion object {
        const val NO_TAG_BUCKET = "__notag__"
        private const val FNV_OFFSET_BASIS = -3750763034362895579L // 14695981039346656037 (unsigned)
        private const val FNV_PRIME = 1099511628211L
    }
}
