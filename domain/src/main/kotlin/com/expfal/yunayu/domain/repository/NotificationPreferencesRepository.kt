package com.expfal.yunayu.domain.repository

/** 本地通知去重偏好：避免重复推送订阅提醒。 */
interface NotificationPreferencesRepository {

    suspend fun wasSubscriptionReminderSent(reminderKey: String): Boolean

    suspend fun markSubscriptionReminderSent(reminderKey: String)
}
