package com.jk.offermate.work

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.jk.offermate.MainActivity
import com.jk.offermate.R

/**
 * 后台分析完成通知。渠道在初始化时创建；Android 13+ 未授予通知权限时静默跳过，避免崩溃。
 */
class NotificationHelper(private val context: Context) {

    init {
        val channel = NotificationChannel(CHANNEL_ID, "面经分析", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "帖子分析进度与结果通知"
        }
        context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
    }

    fun notifyDone(title: String, text: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(launchAppIntent())
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(nextId(), notification)
    }

    /** 前台进度通知（分析进行中，常驻不可滑除）。 */
    fun buildProgressNotification(text: String): Notification =
        NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("正在分析面经")
            .setContentText(text)
            .setContentIntent(launchAppIntent())
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

    /** 点击通知时打开 App 主界面。singleTask 启动模式下会复用已有实例。 */
    private fun launchAppIntent(): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun nextId(): Int = (System.currentTimeMillis() % Int.MAX_VALUE).toInt()

    companion object {
        const val CHANNEL_ID = "offermate_analysis"
        const val FOREGROUND_ID = 1001
        const val RESUME_FOREGROUND_ID = 1002
    }
}
