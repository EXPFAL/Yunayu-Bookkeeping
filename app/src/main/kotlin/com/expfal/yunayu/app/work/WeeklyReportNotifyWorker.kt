package com.expfal.yunayu.app.work

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.expfal.yunayu.app.R

/**
 * 每周日触发的「本周消费复盘」提醒 Worker。
 *
 * 仅发本地通知，不强制重新生成报告（生成由启动 / 报告页 EnsureReports 负责）。
 */
class WeeklyReportNotifyWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        ensureChannel(applicationContext)
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_agenda)
            .setContentTitle(applicationContext.getString(R.string.app_name))
            .setContentText("本周消费复盘已更新")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()
        return try {
            NotificationManagerCompat.from(applicationContext)
                .notify(NOTIFICATION_ID, notification)
            Result.success()
        } catch (_: SecurityException) {
            // API 33+ 未授权通知时静默跳过，不重试。
            Result.success()
        } catch (_: Exception) {
            Result.success()
        }
    }

    companion object {
        const val UNIQUE_WORK_NAME = "weekly_report_notify"
        const val CHANNEL_ID = "weekly_report"
        private const val NOTIFICATION_ID = 2101

        fun ensureChannel(context: Context) {
            val manager = context.getSystemService(NotificationManager::class.java) ?: return
            val channel = NotificationChannel(
                CHANNEL_ID,
                "周报提醒",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = "每周日提醒查看本周消费复盘"
            }
            manager.createNotificationChannel(channel)
        }
    }
}
