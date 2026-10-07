package com.expfal.yunayu.app.notify

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.expfal.yunayu.app.MainActivity
import com.expfal.yunayu.app.R
import com.expfal.yunayu.domain.repository.MonthlyBudgetRepository
import com.expfal.yunayu.domain.usecase.MonthlyBudgetEngine
import com.expfal.yunayu.domain.util.WeeklyReportSchedule
import com.expfal.yunayu.ui.screen.home.EXTRA_OPEN_WEEKLY_REPORT
import com.expfal.yunayu.ui.util.formatCents
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.util.concurrent.TimeUnit

/**
 * 周日 10:00 轻提醒：本周复盘 + 本周还可花。
 * 点击打开 [MainActivity] 并带 [EXTRA_OPEN_WEEKLY_REPORT]。
 */
class WeeklyReportNotifyWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        ensureChannel(applicationContext)
        val entry = EntryPointAccessors.fromApplication(
            applicationContext,
            WeeklyReportNotifyEntryPoint::class.java,
        )
        val budgetCents = entry.monthlyBudgetRepository().observeMonthlyBudgetCents().first()
        val snapshot = entry.monthlyBudgetEngine().observeSnapshot(LocalDate.now()).first()
        val body = if (budgetCents > 0L) {
            "本周复盘已更新 · 本周还可花 ¥${formatCents(snapshot.weeklyRemainingCents)}"
        } else {
            "本周复盘已更新 · 设置预算后可看每周还能花多少"
        }
        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_OPEN_WEEKLY_REPORT, true)
        }
        val pending = PendingIntent.getActivity(
            applicationContext,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("云娅记账")
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        NotificationManagerCompat.from(applicationContext).notify(NOTIFICATION_ID, notification)
        return Result.success()
    }

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface WeeklyReportNotifyEntryPoint {
        fun monthlyBudgetRepository(): MonthlyBudgetRepository
        fun monthlyBudgetEngine(): MonthlyBudgetEngine
    }

    companion object {
        const val UNIQUE_WORK = "weekly_report_notify"
        const val CHANNEL_ID = "weekly_report"
        private const val NOTIFICATION_ID = 2107
        private const val REQUEST_CODE = 2107

        /** 注册每周一次、首次对齐到下个周日 10:00 的 PeriodicWork。 */
        fun schedule(context: Context) {
            ensureChannel(context)
            val request = PeriodicWorkRequestBuilder<WeeklyReportNotifyWorker>(7, TimeUnit.DAYS)
                .setInitialDelay(WeeklyReportSchedule.millisUntilNextSunday10(), TimeUnit.MILLISECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_WORK,
                ExistingPeriodicWorkPolicy.UPDATE,
                request,
            )
        }

        private fun ensureChannel(context: Context) {
            val manager = context.getSystemService(NotificationManager::class.java) ?: return
            val channel = NotificationChannel(
                CHANNEL_ID,
                "周日复盘",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = "每周日提醒查看本周还可花"
            }
            manager.createNotificationChannel(channel)
        }
    }
}
