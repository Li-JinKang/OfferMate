package com.jk.offermate.work

import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.jk.offermate.OfferMateApplication
import com.jk.offermate.data.importer.ImportResult
import com.jk.offermate.domain.model.ImportStatus
import kotlinx.coroutines.flow.first

/**
 * 后台分析任务：读取链接/粘贴正文 → AI 分析 → 落库 → 通知。
 * 依赖从 Application 的 [com.jk.offermate.di.AppContainer] 获取。
 */
class AnalyzePostWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as OfferMateApplication).container
        val store = container.postStore
        val notifier = NotificationHelper(applicationContext)

        val id = inputData.getString(KEY_ID) ?: return Result.failure()
        val url = inputData.getString(KEY_URL).orEmpty()
        val mode = inputData.getString(KEY_MODE) ?: MODE_URL
        val text = inputData.getString(KEY_TEXT).orEmpty()

        runCatching { setForeground(getForegroundInfo()) }

        return try {
            store.markStatus(id, ImportStatus.ANALYZING)
            val profile = container.resumeRepository.profile.first()
            if (profile.targetRole.isBlank()) {
                store.markFailed(id)
                notifier.notifyDone("分析未开始", "请先在\"我的\"里填写目标岗位/简历")
                return Result.success()
            }

            val result = if (mode == MODE_TEXT) {
                container.importInteractor.importFromText(text, profile, url)
            } else {
                container.importInteractor.importFromUrl(url, profile)
            }

            when (result) {
                is ImportResult.Success -> {
                    val title = result.content.title.ifBlank { "面经解析" }
                    store.saveSuccess(id, title, result.content.text.take(140), result.questions)
                    notifier.notifyDone("《$title》已整理", "为你整理了 ${result.questions.size} 道相关题")
                }
                is ImportResult.NeedsManualInput -> {
                    store.markNeedsManual(id)
                    notifier.notifyDone("需要手动粘贴", "自动读取失败，请在应用内粘贴正文后重试")
                }
                is ImportResult.Failed -> {
                    store.markFailed(id)
                    notifier.notifyDone("分析失败", result.reason)
                }
            }
            Result.success()
        } catch (t: Throwable) {
            store.markFailed(id)
            Result.success()
        }
    }

    override suspend fun getForegroundInfo(): ForegroundInfo {
        val notification = NotificationHelper(applicationContext).buildProgressNotification("正在读取并分析面经…")
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ForegroundInfo(NotificationHelper.FOREGROUND_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(NotificationHelper.FOREGROUND_ID, notification)
        }
    }

    companion object {
        const val KEY_ID = "id"
        const val KEY_URL = "url"
        const val KEY_MODE = "mode"
        const val KEY_TEXT = "text"
        const val MODE_URL = "url"
        const val MODE_TEXT = "text"
    }
}
