package com.jk.offermate.work

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.jk.offermate.data.local.PostStore
import com.jk.offermate.domain.model.ImportStatus
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * 导入任务入队抽象（便于 ViewModel 单测替换）。
 */
interface ImportScheduler {
    suspend fun enqueueUrl(url: String): String
    suspend fun enqueueText(text: String, sourceUrl: String = ""): String

    /**
     * 重跑一条已存在的导入记录（用户在失败卡片上点「重试」）。
     * 复用原 id，不新建卡片；状态先回落 PENDING 并清掉旧的失败原因。
     */
    suspend fun retryUrl(id: String, url: String)

    /**
     * 将简历 AI 分析任务加入 WorkManager 队列。
     * 使用 [ExistingWorkPolicy.REPLACE]：若队列中已有同名任务则替换。
     * 任务失败（如 Key 未配置）时不自动重试；由调用方在 Key 更新后重新入队。
     */
    fun enqueueResumeAnalysis()
}

/**
 * 基于 WorkManager 的实现：先落待处理记录（首页立即可见），再后台执行；
 * 进程被杀/重启后 WorkManager 会恢复未完成任务。
 */
class WorkManagerImportScheduler(
    private val context: Context,
    private val postStore: PostStore
) : ImportScheduler {

    override suspend fun enqueueUrl(url: String): String {
        val id = UUID.randomUUID().toString()
        postStore.createPending(id, url)
        enqueue(
            id,
            workDataOf(
                AnalyzePostWorker.KEY_ID to id,
                AnalyzePostWorker.KEY_URL to url,
                AnalyzePostWorker.KEY_MODE to AnalyzePostWorker.MODE_URL
            )
        )
        return id
    }

    override suspend fun enqueueText(text: String, sourceUrl: String): String {
        val id = UUID.randomUUID().toString()
        postStore.createPending(id, sourceUrl.ifBlank { "手动粘贴" })
        enqueue(
            id,
            workDataOf(
                AnalyzePostWorker.KEY_ID to id,
                AnalyzePostWorker.KEY_URL to sourceUrl,
                AnalyzePostWorker.KEY_TEXT to text,
                AnalyzePostWorker.KEY_MODE to AnalyzePostWorker.MODE_TEXT
            )
        )
        return id
    }

    override suspend fun retryUrl(id: String, url: String) {
        postStore.markStatus(id, ImportStatus.PENDING)
        enqueue(
            id,
            workDataOf(
                AnalyzePostWorker.KEY_ID to id,
                AnalyzePostWorker.KEY_URL to url,
                AnalyzePostWorker.KEY_MODE to AnalyzePostWorker.MODE_URL
            ),
            // 上一轮的 unique work 已终结，KEEP 语义下不会被复用，显式 REPLACE 更直白
            policy = ExistingWorkPolicy.REPLACE
        )
    }

    override fun enqueueResumeAnalysis() {
        val request = OneTimeWorkRequestBuilder<AnalyzeResumeWorker>()
            .setConstraints(
                Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            AnalyzeResumeWorker.WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    private fun enqueue(id: String, data: Data, policy: ExistingWorkPolicy = ExistingWorkPolicy.KEEP) {
        val request = OneTimeWorkRequestBuilder<AnalyzePostWorker>()
            .setInputData(data)
            .setConstraints(
                Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
            )
            // 退避起点 30s：网络抖动通常几十秒内自愈，10s 起太急，容易在同一次故障窗口内连试三次白费
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, RETRY_BACKOFF_SECONDS, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork("analyze_$id", policy, request)
    }

    private companion object {
        const val RETRY_BACKOFF_SECONDS = 30L
    }
}
