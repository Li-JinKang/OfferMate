package com.jk.offermate.data.reader

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONTokener
import kotlin.coroutines.resume

/**
 * 用离屏 [WebView] 在真实浏览器环境中读取页面正文，作为静态抓取失败后的降级方案。
 *
 * 相比 OkHttp 静态抓取的优势：
 * - 真实浏览器 UA + Cookie，并会**执行 JS**：能跟随 `xhslink.cn` 这类短链的重定向/JS 跳转，
 *   拿到展开后的真实笔记页（解决"短链未展开"）。
 * - 页面渲染后再取 DOM，小红书的 `window.__INITIAL_STATE__`（正文所在）此时已注入，
 *   直接复用 [HtmlContentExtractor]（内含 `XhsNoteExtractor`）解析。
 *
 * 说明：
 * - 必须在主线程操作 WebView，这里用 `Dispatchers.Main` 切换。
 * - 未注册任何 JS 桥（`addJavascriptInterface`），仅读取渲染后的 DOM 文本，降低加载不受信任页面的风险。
 * - 渲染效果依赖设备与目标站点风控，需真机验证；纯文本解析逻辑仍由已测的 [HtmlContentExtractor] 承担。
 */
class WebViewContentReader(
    private val context: Context,
    private val extractor: HtmlContentExtractor,
    private val timeoutMs: Long = 20_000,
    private val settleDelayMs: Long = 800,
    private val maxPolls: Int = 8,
    private val pollIntervalMs: Long = 700
) : DynamicContentReader {

    @SuppressLint("SetJavaScriptEnabled")
    override suspend fun read(url: String): PostContent? = withContext(Dispatchers.Main) {
        withTimeoutOrNull(timeoutMs) {
            val webView = WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                // 只为读文字，禁图片以提速省流
                settings.loadsImagesAutomatically = false
                settings.blockNetworkImage = true
                settings.userAgentString = MOBILE_UA
            }
            try {
                awaitInitialLoad(webView, url)
                // 等 SPA 渲染与 __INITIAL_STATE__ 注入，随后轮询 DOM 直到拿到可用正文
                delay(settleDelayMs)
                repeat(maxPolls) {
                    val html = webView.currentHtml()
                    if (html != null) {
                        val finalUrl = webView.url ?: url
                        val content = runCatching { extractor.extract(html, finalUrl) }.getOrNull()
                        if (content != null && content.isUsable) {
                            Log.d(TAG, "webview success finalUrl=$finalUrl len=${content.text.length}")
                            return@withTimeoutOrNull content.copy(method = ExtractionMethod.WEBVIEW)
                        }
                    }
                    delay(pollIntervalMs)
                }
                Log.w(TAG, "webview no usable content for $url")
                null
            } finally {
                webView.stopLoading()
                webView.destroy()
            }
        }
    }

    /** 挂起直到首个 `onPageFinished`（含重定向后的页面）。 */
    private suspend fun awaitInitialLoad(webView: WebView, url: String) =
        suspendCancellableCoroutine<Unit> { cont ->
            webView.webViewClient = object : WebViewClient() {
                private var resumed = false
                override fun onPageFinished(view: WebView?, finishedUrl: String?) {
                    if (!resumed) {
                        resumed = true
                        if (cont.isActive) cont.resume(Unit)
                    }
                }
            }
            webView.loadUrl(url)
        }

    /** 取当前渲染后的整页 HTML（`evaluateJavascript` 返回的是 JSON 编码字符串，需解码）。 */
    private suspend fun WebView.currentHtml(): String? =
        suspendCancellableCoroutine { cont ->
            evaluateJavascript("(function(){return document.documentElement.outerHTML;})();") { value ->
                if (cont.isActive) cont.resume(decodeJsString(value))
            }
        }

    private fun decodeJsString(value: String?): String? {
        if (value.isNullOrBlank() || value == "null") return null
        return runCatching { JSONTokener(value).nextValue() as? String }.getOrNull()
    }

    companion object {
        private const val TAG = "OfferMate"
        const val MOBILE_UA =
            "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
    }
}
