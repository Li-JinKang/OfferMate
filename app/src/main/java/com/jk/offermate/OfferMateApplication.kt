package com.jk.offermate

import android.app.Application
import android.util.Log
import com.jk.offermate.di.AppContainer
import com.jk.offermate.di.DefaultAppContainer
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
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

        seedDevApiKeyIfBlank()
    }

    /**
     * 开发期便利：把 `local.properties` 里的 `devApiKey` 回填进加密存储。
     *
     * 存在的理由是 API Key 恰好是唯一"备份脚本救不回来"的配置——它由
     * [com.jk.offermate.data.settings.EncryptedPrefsKeyStore] 加密，主密钥在 Android Keystore
     * 里按 app UID 归属，随包卸载一起删除，拷密文文件回来也解不开。
     * 其余数据（Room 库 / DataStore / 简历记忆文件）都能用 `tools/dev-backup.sh` 带走。
     *
     * 只在 Key **为空**时写入，绝不覆盖用户在设置页手填的值。
     * release 变体的 `DEV_API_KEY` 是空串（默认值在 `defaultConfig` 里给），因此直接返回。
     */
    private fun seedDevApiKeyIfBlank() {
        val key = BuildConfig.DEV_API_KEY
        if (key.isBlank()) return

        appScope.launch {
            // 不用 runCatching：会把 CancellationException 一起吞掉（项目红线，
            // 参见 ImportInteractor.tolerate / AnalyzePostWorker.safely）
            try {
                val settings = container.settingsRepository
                val providerId = BuildConfig.DEV_API_PROVIDER.ifBlank {
                    settings.activeProviderId.first()
                }
                val current = settings.config(providerId).first()
                if (current.apiKey.isNotBlank()) return@launch

                // model / baseUrl 在未配置时读到的就是该服务商的默认值，原样回写即保持默认
                settings.enableProvider(
                    providerId = providerId,
                    apiKey = key,
                    model = current.model,
                    baseUrl = current.baseUrl
                )
                Log.i("OfferMate", "已从 local.properties 回填 $providerId 的 API Key")
            } catch (c: CancellationException) {
                throw c
            } catch (e: Exception) {
                Log.w("OfferMate", "开发期 Key 回填失败：${e.message}")
            }
        }
    }
}
