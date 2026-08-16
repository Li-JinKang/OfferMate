package com.jk.offermate.data.local.dao

/** 去重比对用的题目指纹投影（Room 查询结果）。 */
data class FingerprintRow(
    val exactHash: String,
    val simhash: Long,
    val bucketKey: String
)
