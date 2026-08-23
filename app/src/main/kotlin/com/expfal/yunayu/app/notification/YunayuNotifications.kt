package com.expfal.yunayu.app.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.expfal.yunayu.app.MainActivity
import com.expfal.yunayu.domain.usecase.SubscriptionReminder
import com.expfal.yunayu.domain.usecase.WeeklyReportNotification
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** 创建通知渠道并发送订阅 / 周报本地通知。 */
@Singleton
class YunayuNotifications @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    init {
        createChannels()
    }

    fun showSubscriptionReminder(reminder: SubscriptionReminder) {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return
        val content = "「${reminder.name}」将在 3 天后扣费，可在订阅开支中记一笔"
        val notification = NotificationCompat.Builder(context, CHANNEL_SUBSCRIPTION)
            .setSmallIcon(android.R.drawable.ic_menu_my_calendar)
            .setContentTitle("订阅扣费提醒")
            .setContentText(content)
            .setStyle(NotificationCompat.BigTextStyle().bigText(content))
            .setContentIntent(mainActivityPendingIntent())
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(
            NOTIFICATION_ID_SUBSCRIPTION_BASE + reminder.subscriptionId.toInt(),
            notification,
        )
    }

    fun showWeeklyReport(notification: WeeklyReportNotification) {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return
        val expenseYuan = notification.expenseCents / 100.0
        val content = "上周消费约 ¥${"%.2f".format(expenseYuan)}，打开查看完整周报"
        val built = NotificationCompat.Builder(context, CHANNEL_WEEKLY_REPORT)
            .setSmallIcon(android.R.drawable.ic_menu_agenda)
            .setContentTitle("上周消费周报")
            .setContentText(content)
            .setStyle(NotificationCompat.BigTextStyle().bigText(content))
            .setContentIntent(mainActivityPendingIntent())
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID_WEEKLY_REPORT, built)
    }

    private fun mainActivityPendingIntent(): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun createChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_SUBSCRIPTION,
                "订阅扣费提醒",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = "订阅到期前 3 天提醒"
            },
        )
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_WEEKLY_REPORT,
                "消费周报",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = "上周消费报告生成后通知"
            },
        )
    }

    companion object {
        private const val CHANNEL_SUBSCRIPTION = "subscription_reminders"
        private const val CHANNEL_WEEKLY_REPORT = "weekly_reports"
        private const val NOTIFICATION_ID_SUBSCRIPTION_BASE = 20_000
        private const val NOTIFICATION_ID_WEEKLY_REPORT = 30_000
    }
}
