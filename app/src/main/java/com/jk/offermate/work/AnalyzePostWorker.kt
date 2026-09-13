package com.jk.offermate.work

import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.jk.offermate.OfferMateApplication
import com.jk.offermate.data.importer.ImportResult
import com.jk.offermate.data.local.PostStore
import com.jk.offermate.data.net.NetErrors
import com.jk.offermate.domain.model.ImportStatus
import kotlinx.coroutines.CancellationException

/**
 * 后台分析任务：读取链接/粘贴正文 → AI 分析 → 落库 → 通知。
 * 依赖从 Application 的 [com.jk.offermate.di.AppContainer] 获取。
 *
 * 失败策略（见 docs/plan/network-resilience.md 第 3 节）：
 * - **可重试**（网络抖动 / 限流 / 5xx）：状态回落 PENDING + [Result.retry]，由 WorkManager 指数退避重试，
 *   上限 [MAX_ATTEMPTS] 次；此前这类瞬时故障被一次性判死，用户只能手动重来。
 * - **不可重试**（Key 无效、余额不足、模型输出不可解析）：落 FAILED **并带上原因**，同时发通知。
 * - [CancellationException] 直接上抛：任务被取消不是失败，不能落 FAILED，
 *   否则还会在已取消的 scope 上写库二次失败。
 * - 兜底写库与通知都单独包 try/catch：兜底逻辑自身抛异常会让 worker 静默变成 [Result.failure]。
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

        Log.d(TAG, "start id=$id mode=$mode url=$url textLen=${text.length} attempt=$runAttemptCount")
        runCatching { setForeground(getForegroundInfo()) }
            .onFailure { Log.w(TAG, "setForeground failed: ${it.message}") }

        return try {
            // 串行闸门：避免并发任务共用同一条 HTTP/2 连接一起阵亡，也避免并发烧 token
            AnalyzeGate.withSlot {
                // 读取阶段单独可见（此前直接跳到 ANALYZING，读取卡住时用户看不出区别）
                store.markStatus(id, if (mode == MODE_TEXT) ImportStatus.ANALYZING else ImportStatus.READING)

                Log.d(TAG, "calling importInteractor…")
                val result = if (mode == MODE_TEXT) {
                    container.importInteractor.importFromText(text, url)
                } else {
                    store.markStatus(id, ImportStatus.ANALYZING)
                    container.importInteractor.importFromUrl(url)
                }

                handleResult(id, result, store, notifier)
            }
        } catch (c: CancellationException) {
            // 取消不是失败：保持当前状态，WorkManager 之后会重新调度
            Log.d(TAG, "任务被取消 id=$id")
            throw c
        } catch (t: Throwable) {
            handleUnexpected(id, t, store, notifier)
        }
    }

    private suspend fun handleResult(
        id: String,
        result: ImportResult,
        store: PostStore,
        notifier: NotificationHelper
    ): Result = when (result) {
        is ImportResult.Success -> {
            val title = result.content.title.ifBlank { "面经解析" }
            val images = result.content.imageUrls
            Log.d(
                TAG,
                "SUCCESS title='$title' contentLen=${result.content.text.length} " +
                    "questions=${result.questions.size} imageCount=${images.size}"
            )
            images.forEachIndexed { i, u -> Log.d(TAG, "  image[$i]=$u") }
            store.saveSuccess(id, title, result.content.text.take(140), result.questions)
            notifier.notifyDone("《$title》已整理", "为你整理了 ${result.questions.size} 道相关题")
            Result.success()
        }

        is ImportResult.NeedsManualInput -> {
            Log.w(TAG, "NEEDS_MANUAL resolved=${result.resolvedUrl} reason=${result.reason}")
            // 把诊断原因一起落库：此前只 Log，用户在 App 里看不到卡在哪一步
            store.markNeedsManual(id, result.reason)
            notifier.notifyDone("需要手动粘贴", result.reason)
            Result.success()
        }

        is ImportResult.Failed ->
            if (result.retryable && runAttemptCount < MAX_ATTEMPTS - 1) {
                Log.w(TAG, "可重试失败，交给 WorkManager 退避重试（attempt=$runAttemptCount）：${result.reason}")
                safely { store.markStatus(id, ImportStatus.PENDING) }
                Result.retry()
            } else {
                Log.e(TAG, "FAILED retryable=${result.retryable} attempt=$runAttemptCount reason=${result.reason}")
                safely { store.markFailed(id, result.reason) }
                safely { notifier.notifyDone("分析失败", result.reason) }
                Result.success()
            }
    }

    /**
     * 兜底分支：任何未被下层翻译成 [ImportResult] 的异常。
     * 瞬时网络故障仍然重试；其余落终态失败，并保证"有原因 + 有通知"。
     */
    private suspend fun handleUnexpected(
        id: String,
        t: Throwable,
        store: PostStore,
        notifier: NotificationHelper
    ): Result {
        val transient = NetErrors.isTransient(t)
        Log.e(TAG, "worker exception id=$id transient=$transient attempt=$runAttemptCount", t)

        if (transient && runAttemptCount < MAX_ATTEMPTS - 1) {
            safely { store.markStatus(id, ImportStatus.PENDING) }
            return Result.retry()
        }

        val reason = NetErrors.userMessage(t)
        safely { store.markFailed(id, reason) }
        safely { notifier.notifyDone("分析失败", reason) }
        return Result.success()
    }

    /** 兜底动作不能再抛：否则 worker 会静默变成 [Result.failure]，用户连失败态都看不到。 */
    private suspend fun safely(block: suspend () -> Unit) {
        try {
            block()
        } catch (c: CancellationException) {
            throw c
        } catch (e: Exception) {
            Log.w(TAG, "兜底动作失败：${e.javaClass.simpleName}:${e.message}")
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
        const val TAG = "OfferMate"
        const val KEY_ID = "id"
        const val KEY_URL = "url"
        const val KEY_MODE = "mode"
        const val KEY_TEXT = "text"
        const val MODE_URL = "url"
        const val MODE_TEXT = "text"

        /** 总尝试次数上限（含首次）。`runAttemptCount` 从 0 开始计。 */
        const val MAX_ATTEMPTS = 3
    }
}
