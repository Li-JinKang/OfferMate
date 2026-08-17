package com.jk.offermate.data.reader

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ContentReaderTest {

    private val extractor = HtmlContentExtractor()

    private fun loadFixture(name: String): String =
        requireNotNull(javaClass.getResourceAsStream("/fixtures/html/$name")) {
            "缺少测试夹具: $name"
        }.bufferedReader().use { it.readText() }

    private class FakeResolver(private val out: String) : UrlResolver {
        override fun resolve(url: String): String = out
    }

    private class FakeFetcher(private val html: String?) : HtmlFetcher {
        override fun fetch(url: String): String? = html
    }

    private class FakeDynamic(private val content: PostContent?) : DynamicContentReader {
        override suspend fun read(url: String): PostContent? = content
    }

    @Test
    fun `static extraction success returns content`(): Unit = runTest {
        val reader = ContentReader(
            urlResolver = FakeResolver("https://www.nowcoder.com/post/123"),
            htmlFetcher = FakeFetcher(loadFixture("nowcoder_sample.html")),
            extractor = extractor,
            dynamicReader = null
        )

        val result = reader.read("https://www.nowcoder.com/share/jump/xxx")

        assertTrue(result is ReadResult.Success)
        val content = (result as ReadResult.Success).content
        assertEquals(ExtractionMethod.STATIC_HTML, content.method)
        assertTrue(content.text.contains("LRU 缓存"))
    }

    @Test
    fun `resolves short link before fetching`() = runTest {
        val resolved = "https://www.xiaohongshu.com/explore/real"
        var fetchedUrl: String? = null
        val reader = ContentReader(
            urlResolver = FakeResolver(resolved),
            htmlFetcher = object : HtmlFetcher {
                override fun fetch(url: String): String? {
                    fetchedUrl = url
                    return loadFixture("nowcoder_sample.html")
                }
            },
            extractor = extractor
        )

        reader.read("https://xhslink.cn/o/shortcode")

        assertEquals("应使用展开后的真实 URL 抓取", resolved, fetchedUrl)
    }

    @Test
    fun `falls back to webview when static content unusable`() = runTest {
        val dynamic = PostContent(
            title = "小红书面经",
            text = "这是通过 WebView 渲染后拿到的正文内容，足够长以通过可用性阈值判断。".repeat(2),
            sourceUrl = "https://www.xiaohongshu.com/explore/real",
            method = ExtractionMethod.WEBVIEW
        )
        val reader = ContentReader(
            urlResolver = FakeResolver("https://www.xiaohongshu.com/explore/real"),
            htmlFetcher = FakeFetcher(loadFixture("xhs_jsonly.html")),
            extractor = extractor,
            dynamicReader = FakeDynamic(dynamic)
        )

        val result = reader.read("https://xhslink.cn/o/shortcode")

        assertTrue(result is ReadResult.Success)
        assertEquals(ExtractionMethod.WEBVIEW, (result as ReadResult.Success).content.method)
    }

    @Test
    fun `needs manual input when both static and webview fail`() = runTest {
        val reader = ContentReader(
            urlResolver = FakeResolver("https://www.xiaohongshu.com/explore/real"),
            htmlFetcher = FakeFetcher(loadFixture("xhs_jsonly.html")),
            extractor = extractor,
            dynamicReader = FakeDynamic(null)
        )

        val result = reader.read("https://xhslink.cn/o/shortcode")

        assertTrue(result is ReadResult.NeedsManualInput)
        assertEquals(
            "https://www.xiaohongshu.com/explore/real",
            (result as ReadResult.NeedsManualInput).resolvedUrl
        )
    }

    @Test
    fun `needs manual input when fetch returns null and no dynamic reader`() = runTest {
        val reader = ContentReader(
            urlResolver = FakeResolver("https://www.nowcoder.com/post/123"),
            htmlFetcher = FakeFetcher(null),
            extractor = extractor,
            dynamicReader = null
        )

        val result = reader.read("https://www.nowcoder.com/share/jump/xxx")

        assertTrue(result is ReadResult.NeedsManualInput)
    }

    @Test
    fun `reason points to short link not expanded when resolved url stays a short link`() = runTest {
        val reader = ContentReader(
            urlResolver = FakeResolver("https://xhslink.cn/o/6Gz0nDGZxAE"), // 解析后仍是短链 = 展开失败
            htmlFetcher = FakeFetcher(null),
            extractor = extractor,
            dynamicReader = null
        )

        val result = reader.read("https://xhslink.cn/o/6Gz0nDGZxAE")

        assertTrue(result is ReadResult.NeedsManualInput)
        assertTrue((result as ReadResult.NeedsManualInput).reason.contains("短链未能展开"))
    }

    @Test
    fun `reason points to fetch failure when page cannot be fetched`() = runTest {
        val reader = ContentReader(
            urlResolver = FakeResolver("https://www.xiaohongshu.com/explore/real"),
            htmlFetcher = FakeFetcher(null),
            extractor = extractor,
            dynamicReader = null
        )

        val result = reader.read("https://xhslink.cn/o/shortcode")

        assertTrue(result is ReadResult.NeedsManualInput)
        assertTrue((result as ReadResult.NeedsManualInput).reason.contains("页面抓取失败"))
    }

    @Test
    fun `reason points to empty body when page fetched but no usable text`() = runTest {
        val reader = ContentReader(
            urlResolver = FakeResolver("https://www.xiaohongshu.com/explore/real"),
            htmlFetcher = FakeFetcher(loadFixture("xhs_jsonly.html")),
            extractor = extractor,
            dynamicReader = null
        )

        val result = reader.read("https://xhslink.cn/o/shortcode")

        assertTrue(result is ReadResult.NeedsManualInput)
        assertTrue((result as ReadResult.NeedsManualInput).reason.contains("未提取到正文"))
    }
}
