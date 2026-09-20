package com.jk.offermate.platform

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [CpuTopology.classify] 的分档规则。
 *
 * 用真实 SoC 的频率组合做样本——分档规则一旦对某类拓扑判错，后果是线程池开得过大或过小，
 * 而这在真机上很难归因（表现只是"某些机型上导入偏慢"）。
 */
class CpuTopologyTest {

    /** 按「核心数 to 最大频率(kHz)」构造 sysfs 读数，核心号从 0 连续分配。 */
    private fun cores(vararg clusters: Pair<Int, Long>): Map<Int, Long> {
        val map = LinkedHashMap<Int, Long>()
        var id = 0
        clusters.forEach { (count, freq) -> repeat(count) { map[id++] = freq } }
        return map
    }

    @Test
    fun `骁龙 8 Gen1 式的四档拆成大中小且不丢核`() {
        // 1 × X2 3.0G + 3 × A710 2.5G + 4 × A510 1.8G
        val topology = CpuTopology.classify(cores(1 to 3_000_000L, 3 to 2_500_000L, 4 to 1_800_000L))!!

        assertEquals(listOf(0), topology.big)
        assertEquals(listOf(1, 2, 3), topology.middle)
        assertEquals(listOf(4, 5, 6, 7), topology.small)
        assertTrue(topology.isHeterogeneous)
        assertEquals(4, topology.performanceCoreCount)
    }

    /**
     * 四档以上时中间档必须**全部**并进中核。只取次高档会让一整档核心既不在大核
     * 也不在小核里，按档取并集时被整簇漏掉。
     */
    @Test
    fun `四档 SoC 的中间两档都并入中核`() {
        // 1 + 2 + 2 + 4：中间的 2 + 2 都该算中核
        val topology = CpuTopology.classify(
            cores(1 to 3_300_000L, 2 to 2_900_000L, 2 to 2_400_000L, 4 to 1_900_000L)
        )!!

        assertEquals(listOf(0), topology.big)
        assertEquals(listOf(1, 2, 3, 4), topology.middle)
        assertEquals(listOf(5, 6, 7, 8), topology.small)
        assertEquals(5, topology.performanceCoreCount)
        // 不丢核
        assertEquals(9, topology.big.size + topology.middle.size + topology.small.size)
    }

    @Test
    fun `两档的中低端机也要能分出大小核`() {
        // 典型 4 大 + 4 小，中核为空
        val topology = CpuTopology.classify(cores(4 to 2_000_000L, 4 to 1_500_000L))!!

        assertEquals(listOf(0, 1, 2, 3), topology.big)
        assertEquals(emptyList<Int>(), topology.middle)
        assertEquals(listOf(4, 5, 6, 7), topology.small)
        assertTrue(topology.isHeterogeneous)
        assertEquals(4, topology.performanceCoreCount)
    }

    /** 同构 CPU 没有分档意义：全算大核，且要能被识别为「非异构」。 */
    @Test
    fun `同构 CPU 全部算大核`() {
        val topology = CpuTopology.classify(cores(4 to 1_800_000L))!!

        assertEquals(listOf(0, 1, 2, 3), topology.big)
        assertTrue(topology.middle.isEmpty())
        assertTrue(topology.small.isEmpty())
        assertFalse(topology.isHeterogeneous)
        assertEquals(4, topology.performanceCoreCount)
    }

    /** 读不到任何频率（厂商裁剪 sysfs / 无权限）时返回 null，由 read() 兜底。 */
    @Test
    fun `没有读数时返回 null`() {
        assertNull(CpuTopology.classify(emptyMap()))
    }

    /**
     * 离线核心的频率文件读不到，于是核心号不连续。分档必须按频率而不是按核心号区间来，
     * 否则会把空洞两侧的核心错分。
     */
    @Test
    fun `核心号不连续也按频率分档`() {
        // cpu3 离线（缺 3），cpu0 是大核，cpu1/2 中核，cpu4-7 小核
        val readings = mapOf(
            0 to 3_000_000L,
            1 to 2_500_000L,
            2 to 2_500_000L,
            4 to 1_800_000L,
            5 to 1_800_000L,
            6 to 1_800_000L,
            7 to 1_800_000L
        )
        val topology = CpuTopology.classify(readings)!!

        assertEquals(listOf(0), topology.big)
        assertEquals(listOf(1, 2), topology.middle)
        assertEquals(listOf(4, 5, 6, 7), topology.small)
    }

    /** performanceCoreCount 至少为 1，别让调用方拿 0 去建线程池。 */
    @Test
    fun `性能核数至少为一`() {
        val topology = CpuTopology.classify(cores(1 to 2_000_000L))!!
        assertEquals(1, topology.performanceCoreCount)
    }

    /** 真机读取不抛异常（JVM 单测里 sysfs 不存在，必然走兜底路径）。 */
    @Test
    fun `读取失败时退化为全部核心`() {
        val topology = CpuTopology.read()
        assertTrue(topology.performanceCoreCount >= 1)
        assertEquals(
            Runtime.getRuntime().availableProcessors().coerceAtLeast(1),
            topology.big.size
        )
    }
}
