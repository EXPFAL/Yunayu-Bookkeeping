package com.expfal.yunayu.domain.repository

/** 本地通知去重偏好：避免重复推送订阅提醒。 */
interface NotificationPreferencesRepository {

    suspend fun wasSubscriptionReminderSent(reminderKey: String): Boolean

    suspend fun markSubscriptionReminderSent(reminderKey: String)

    /** 备份导出：读取全部订阅提醒去重键。 */
    suspend fun getSubscriptionReminderKeys(): Set<String>

    /** 备份导入：整表替换订阅提醒去重键。 */
    suspend fun replaceSubscriptionReminderKeys(keys: Set<String>)
}
