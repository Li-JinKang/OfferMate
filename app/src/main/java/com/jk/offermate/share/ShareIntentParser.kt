package com.jk.offermate.share

/**
 * 解析来自系统分享（`ACTION_SEND`, `text/plain`）的文本，抽出其中的链接。
 *
 * 分享文本形态不统一：有的 App（如浏览器）分享内容就是纯链接；有的（如部分 App 的"分享到"）
 * 会把标题/描述和链接拼在一起，例如 `"这道面试题真难 https://xhslink.cn/o/xxx"`。
 * 这里只做**纯文本**解析，不依赖 Android API，便于 JVM 单测覆盖。
 */
object ShareIntentParser {

    private val URL_REGEX = Regex("""https?://[^\s]+""")

    /**
     * 从分享文本中提取第一个链接；找不到则返回 null（调用方可将全文本当作"手动粘贴正文"处理）。
     */
    fun extractLink(sharedText: String?): String? {
        if (sharedText.isNullOrBlank()) return null
        return URL_REGEX.find(sharedText)?.value?.trimEnd('.', ',', '，', '。', ')', '）')
    }
}
