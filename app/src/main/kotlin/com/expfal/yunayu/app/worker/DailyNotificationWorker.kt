package com.expfal.yunayu.app.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.expfal.yunayu.app.notification.YunayuNotifications
import com.expfal.yunayu.domain.repository.NotificationPreferencesRepository
import com.expfal.yunayu.domain.usecase.CheckSubscriptionRemindersUseCase
import com.expfal.yunayu.domain.usecase.CheckWeeklyReportNotificationUseCase
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/** 每日检查订阅扣费提醒（提前 3 天）与上周周报通知。 */
@HiltWorker
class DailyNotificationWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val checkSubscriptionRemindersUseCase: CheckSubscriptionRemindersUseCase,
    private val checkWeeklyReportNotificationUseCase: CheckWeeklyReportNotificationUseCase,
    private val notificationPreferencesRepository: NotificationPreferencesRepository,
    private val notifications: YunayuNotifications,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = runCatching {
        checkSubscriptionRemindersUseCase().forEach { reminder ->
            notifications.showSubscriptionReminder(reminder)
            notificationPreferencesRepository.markSubscriptionReminderSent(reminder.reminderKey)
        }
        checkWeeklyReportNotificationUseCase()?.let { weekly ->
            notifications.showWeeklyReport(weekly)
            notificationPreferencesRepository.markWeeklyReportNotified(weekly.periodKey)
        }
    }.fold(
        onSuccess = { Result.success() },
        onFailure = { Result.retry() },
    )
}
