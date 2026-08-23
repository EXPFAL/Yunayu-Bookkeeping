package com.expfal.yunayu.domain.repository

/** 本地通知去重偏好：避免重复推送订阅提醒与周报。 */
interface NotificationPreferencesRepository {

    suspend fun wasSubscriptionReminderSent(reminderKey: String): Boolean

    suspend fun markSubscriptionReminderSent(reminderKey: String)

    suspend fun wasWeeklyReportNotified(periodKey: String): Boolean

    suspend fun markWeeklyReportNotified(periodKey: String)
}
