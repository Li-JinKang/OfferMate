package com.jk.offermate.data.settings

/** 全局设置常量（相关性阈值等非按服务商的设置）。 */
object AppSettings {
    const val DEFAULT_THRESHOLD = 60
    const val MIN_THRESHOLD = 0
    const val MAX_THRESHOLD = 100

    fun clampThreshold(value: Int): Int = value.coerceIn(MIN_THRESHOLD, MAX_THRESHOLD)
}
