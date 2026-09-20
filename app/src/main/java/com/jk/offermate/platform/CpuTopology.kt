package com.jk.offermate.platform

import java.io.File

/**
 * CPU 核心分档（大核 / 中核 / 小核）。
 *
 * 移动端几乎都是异构多核（big.LITTLE、DynamIQ），同一颗 SoC 上核心的峰值频率差一倍以上很常见。
 * 内核把每个核的最大频率暴露在 sysfs 里，按频率聚类就能还原出分档——这是**不需要 NDK**
 * 就能拿到的设备能力信息。
 *
 * ## 拿它来做什么
 *
 * **不是用来绑核。** `sched_setaffinity` 只能通过 JNI 调用，而且在 Android 上有三个硬约束：
 * 1. 线程的可用核心受 cgroup cpuset 限制，affinity 只能在 cpuset 的**子集**里选。
 *    后台进程被关进小核 cpuset 时，把 mask 设成全核既不生效也不报错。
 * 2. `sched_setaffinity` 会偶发 `EINVAL`——核心被 hotplug 下线时指定它就会失败。
 * 3. 绑定会**剥夺调度器的迁移能力**：线程被抢占后只能回到原来那个核，不能迁到空闲核。
 *    绑在繁忙的大核上排队，往往比让 EAS 自己挑更慢。
 *
 * 所以这里只把分档当作**并行度的依据**：CPU 密集任务的线程池开多大，应该看
 * [performanceCoreCount] 而不是 `availableProcessors()`。后者在 8 核设备上返回 8，
 * 而其中 4 个小核的单核性能可能只有大核的三分之一,按 8 开池会让任务被拆到小核上拖慢整体，
 * 还会把大核让给别的线程。
 *
 * @property big 频率最高的一档
 * @property middle 中间档（可能为空；三档以上时把中间所有档位合并到这里）
 * @property small 频率最低的一档（同构 CPU 时为空）
 */
data class CpuTopology(
    val big: List<Int>,
    val middle: List<Int>,
    val small: List<Int>
) {

    /** 是否读到了有意义的分档。同构 CPU（只有一个频率档）为 false。 */
    val isHeterogeneous: Boolean get() = small.isNotEmpty()

    /**
     * 适合跑 CPU 密集任务的核心数 = 大核 + 中核。
     *
     * 刻意**不含小核**：把一个 200ms 的解析任务分到小核上，它只会更慢，同时还占着调度资源。
     * 至少返回 1。
     */
    val performanceCoreCount: Int get() = (big.size + middle.size).coerceAtLeast(1)

    companion object {

        private const val CPU_DIR = "/sys/devices/system/cpu"
        private const val MAX_FREQ_RELATIVE_PATH = "cpufreq/cpuinfo_max_freq"
        private val CPU_NAME = Regex("^cpu\\d+$")

        /**
         * 读取当前设备的分档。
         *
         * sysfs 读不到时（厂商裁剪、权限、路径变更）退化成「全部核心都算大核」，
         * 这样 [performanceCoreCount] 等于 `availableProcessors()`，行为与不做这件事一致。
         * **不抛异常**：这是一条尽力而为的能力探测，任何失败都不该影响功能。
         */
        fun read(): CpuTopology = classify(readMaxFrequencies()) ?: fallback()

        private fun fallback(): CpuTopology {
            val count = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)
            return CpuTopology(big = (0 until count).toList(), middle = emptyList(), small = emptyList())
        }

        private fun readMaxFrequencies(): Map<Int, Long> {
            val children = try {
                File(CPU_DIR).listFiles { _, name -> CPU_NAME.matches(name) }
            } catch (error: Throwable) {
                // SecurityException / 厂商 ROM 的各种异常都在这里咽掉，交给 fallback
                null
            } ?: return emptyMap()

            val result = HashMap<Int, Long>(children.size)
            children.forEach { dir ->
                val core = dir.name.removePrefix("cpu").toIntOrNull() ?: return@forEach
                val freq = try {
                    File(dir, MAX_FREQ_RELATIVE_PATH).takeIf { it.canRead() }
                        ?.readText()?.trim()?.toLongOrNull()
                } catch (error: Throwable) {
                    null
                }
                // 离线核心读不到频率文件，这里跳过即可——它上线后频率与同簇其它核一致，
                // 不影响分档结果（同簇核心共享 cpufreq policy）。
                if (freq != null && freq > 0) result[core] = freq
            }
            return result
        }

        /**
         * 把「核心号 → 最大频率」聚类成分档。**纯函数，与 sysfs 解耦以便单测。**
         *
         * 规则按档位数量分三种情况，比「必须恰好三档才工作」宽容——两档的中低端机
         * （典型 4 大 + 4 小）同样需要知道哪些核性能更好：
         * - 1 档（同构）→ 全部算大核，[isHeterogeneous] 为 false；
         * - 2 档 → 高频档为大核，低频档为小核，中核为空；
         * - ≥3 档 → 最高档为大核，最低档为小核，**中间所有档位合并**为中核。
         *
         * 合并中间档而不是只取次高档，是为了不丢核：10 核 4 档的 SoC（1+3+2+4 这类）
         * 若只认次高档，会有一整档核心既不在大核也不在小核里，在按档取并集时被漏掉。
         *
         * @return 读不到任何频率时返回 null，交由调用方兜底
         */
        internal fun classify(maxFreqByCore: Map<Int, Long>): CpuTopology? {
            if (maxFreqByCore.isEmpty()) return null

            // 频率降序分组；同频核心视为同一簇（它们共享 cpufreq policy）
            val clusters: List<List<Int>> = maxFreqByCore.entries
                .groupBy({ it.value }, { it.key })
                .toSortedMap(reverseOrder())
                .values
                .map { it.sorted() }

            return when (clusters.size) {
                1 -> CpuTopology(big = clusters[0], middle = emptyList(), small = emptyList())
                2 -> CpuTopology(big = clusters[0], middle = emptyList(), small = clusters[1])
                else -> CpuTopology(
                    big = clusters.first(),
                    middle = clusters.subList(1, clusters.lastIndex).flatten().sorted(),
                    small = clusters.last()
                )
            }
        }
    }
}
