package com.jk.offermate

import android.app.Application
import android.util.Log
import com.jk.offermate.di.AppContainer
import com.jk.offermate.di.DefaultAppContainer
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Application 持有全局依赖容器（手动 DI 的组合根）。
 */
class OfferMateApplication : Application() {
    lateinit var container: AppContainer
        private set

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        // PdfBox-Android 资源初始化（用于简历 PDF 解析）
        PDFBoxResourceLoader.init(applicationContext)
        container = DefaultAppContainer(applicationContext)

        // 后台发现外部 MCP 服务器工具（best-effort，失败不影响启动/本地工具）
        appScope.launch {
            runCatching { container.mcpToolRepository.refresh() }
                .onFailure { Log.w("OfferMate", "MCP 工具发现失败：${it.message}") }
        }
    }
}
