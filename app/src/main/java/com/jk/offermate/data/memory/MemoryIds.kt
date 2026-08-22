package com.jk.offermate.data.memory

/**
 * 记忆集/细节文件 id 的校验与规整。
 *
 * id 同时用作文件/文件夹名，必须限制为安全字符（防路径穿越），且尽量人可读
 * （如 "java-backend"、"order-system"）。
 */
object MemoryIds {

    private val SAFE = Regex("^[A-Za-z0-9_-]{1,128}$")

    /** 是否为合法 id（非空、仅字母/数字/下划线/连字符、长度 <=128）。 */
    fun isValid(s: String): Boolean = SAFE.matches(s)

    /**
     * 把任意文本（如模型给出的 id 建议、标题）规整为合法 id：小写、非字母数字压成连字符、
     * 去除首尾连字符、截断长度。无有效字符时回退为 [fallback]。
     */
    fun sanitize(raw: String, fallback: String = "profile"): String {
        val slug = raw.trim().lowercase()
            .replace(Regex("[^a-z0-9]+"), "-")
            .trim('-')
            .take(128)
        return slug.ifEmpty { fallback }
    }

    /** 在 [taken] 已占用集合下，为 [base] 生成唯一 id（冲突则追加 -2、-3…）。 */
    fun unique(base: String, taken: Set<String>): String {
        val b = sanitize(base)
        if (b !in taken) return b
        var n = 2
        while ("$b-$n" in taken) n++
        return "$b-$n"
    }
}
