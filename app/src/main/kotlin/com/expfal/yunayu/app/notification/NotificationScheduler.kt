package com.expfal.yunayu.app.notification

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.expfal.yunayu.app.worker.DailyNotificationWorker
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.util.concurrent.TimeUnit

/** 调度每日本地通知检查（订阅提醒 + 周报）。 */
object NotificationScheduler {

    private const val UNIQUE_WORK_NAME = "daily_notifications"

    fun schedule(context: Context) {
        val initialDelayMs = millisUntilNextMorning()
        val request = PeriodicWorkRequestBuilder<DailyNotificationWorker>(1, TimeUnit.DAYS)
            .setInitialDelay(initialDelayMs, TimeUnit.MILLISECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            UNIQUE_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

  /** 距下次本地 8:00 的毫秒数。 */
    private fun millisUntilNextMorning(hour: Int = 8): Long {
        val zone = ZoneId.systemDefault()
        val now = LocalDateTime.now(zone)
        var target = now.toLocalDate().atTime(LocalTime.of(hour, 0)).atZone(zone)
        if (!target.isAfter(now.atZone(zone))) {
            target = target.plusDays(1)
        }
        return Duration.between(now.atZone(zone), target).toMillis()
    }
}
