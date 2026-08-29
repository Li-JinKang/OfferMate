package com.jk.offermate.work

import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.jk.offermate.OfferMateApplication
import com.jk.offermate.agent.AiException
import kotlinx.coroutines.flow.first

/**
 * 后台简历 AI 分析任务：读取已保存的简历文本 → 结构化 → 写入分层记忆文件 → 通知。
 *
 * 触发场景：
 * 1. 用户导入 PDF 简历时（无论是否已配置 Key，均入队）
 * 2. 用户保存/修改识别文本后
 * 3. 用户补配置 API Key 时（检测到 needsAiAnalysis=true 时重新入队）
 *
 * 失败策略：
 * - [AiException]（Key 未配置）→ [Result.failure]，不重试（等用户配置 Key 后重新入队）
 * - 其他网络/IO 异常 → [Result.retry]，WorkManager 指数退避重试
 */
class AnalyzeResumeWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as OfferMateApplication).container
        val repository = container.resumeRepository
        val ingestor = container.resumeIngestor
        val notifier = NotificationHelper(applicationContext)

        runCatching { setForeground(getForegroundInfo()) }
            .onFailure { Log.w(TAG, "setForeground failed: ${it.message}") }

        val rawText = repository.profile.first().rawText
        if (rawText.isBlank()) {
            Log.d(TAG, "rawText 为空，跳过分析")
            repository.setNeedsAiAnalysis(false)
            return Result.success()
        }

        Log.d(TAG, "开始简历 AI 分析，文本长度=${rawText.length}")
        return try {
            ingestor.ingest(rawText)
            repository.setNeedsAiAnalysis(false)
            Log.d(TAG, "简历分析完成")
            notifier.notifyDone("简历分析完成", "记忆已生成，AI 将结合你的简历回答问题")
            Result.success()
        } catch (e: AiException) {
            Log.w(TAG, "AI Key 未配置，等待用户配置后重新触发：${e.message}")
            Result.failure()
        } catch (e: Exception) {
            Log.e(TAG, "简历分析异常，将重试", e)
            Result.retry()
        }
    }

    override suspend fun getForegroundInfo(): ForegroundInfo {
        val notification = NotificationHelper(applicationContext)
            .buildProgressNotification("正在分析简历，生成 AI 记忆…")
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ForegroundInfo(
                NotificationHelper.RESUME_FOREGROUND_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            ForegroundInfo(NotificationHelper.RESUME_FOREGROUND_ID, notification)
        }
    }

    companion object {
        const val TAG = "AnalyzeResume"
        const val WORK_NAME = "analyze_resume"
    }
}
