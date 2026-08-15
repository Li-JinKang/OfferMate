package com.jk.offermate.data.reader

import kotlinx.coroutines.runBlocking
import org.junit.Test

/**
 * 真实联网抓取测试（手动运行，用于人工校验读取效果）。
 *
 * 注意：
 *  - 该测试会真正访问网络，结果依赖目标站点的反爬策略与登录态，可能失败或拿到非正文页面。
 *  - 不做断言，只把读取结果打印到标准输出，供人工核对。
 *  - 与离线单元测试（HtmlContentExtractorTest 等）不同，那些用的是本地夹具、不联网。
 *
 * 运行：
 *   ./gradlew :app:testDebugUnitTest --tests "com.jk.offermate.data.reader.LiveLinkReadingTest" --console=plain
 */
class LiveLinkReadingTest {

    private val reader = ContentReader(
        urlResolver = OkHttpUrlResolver(),
        htmlFetcher = OkHttpHtmlFetcher(),
        extractor = HtmlContentExtractor(),
        dynamicReader = null // 纯静态抓取；小红书这类 JS 渲染页预期会走到"需手动粘贴"
    )

    private val urls = listOf(
        "https://www.nowcoder.com/share/jump/53245626677297352",
        "https://xhslink.cn/o/6Gz0nDGZxAE"
    )

    @Test
    fun readLinksAndPrint() = runBlocking {
        for (url in urls) {
            println("\n==================== 读取: $url ====================")
            try {
                when (val result = reader.read(url)) {
                    is ReadResult.Success -> {
                        val c = result.content
                        println("[成功] 提取方式 = ${c.method}")
                        println("展开后URL = ${c.sourceUrl}")
                        println("标题 = ${c.title}")
                        println("正文长度 = ${c.text.length}, 可用 = ${c.isUsable}")
                        println("---- 正文(最多前 1500 字) ----")
                        println(c.text.take(1500))
                    }
                    is ReadResult.NeedsManualInput -> {
                        println("[需手动粘贴] 展开后URL = ${result.resolvedUrl}")
                        println("原因 = ${result.reason}")
                        println("（静态抓取失败或正文过短；此站点大概率需要 WebView 登录态方案）")
                    }
                }
            } catch (t: Throwable) {
                println("[异常] ${t.javaClass.simpleName}: ${t.message}")
            }
            println("==================== END ====================\n")
        }
    }
}
