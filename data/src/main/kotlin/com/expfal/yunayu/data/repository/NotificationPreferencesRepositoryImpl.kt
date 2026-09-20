package com.expfal.yunayu.data.repository

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.expfal.yunayu.domain.repository.NotificationPreferencesRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.notificationPrefsDataStore by preferencesDataStore(name = "notification_prefs")

@Singleton
class NotificationPreferencesRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
) : NotificationPreferencesRepository {

    override suspend fun wasSubscriptionReminderSent(reminderKey: String): Boolean =
        readSet(KEY_SUBSCRIPTION_REMINDERS).contains(reminderKey)

    override suspend fun markSubscriptionReminderSent(reminderKey: String) {
        context.notificationPrefsDataStore.edit { prefs ->
            val updated = prefs[KEY_SUBSCRIPTION_REMINDERS].orEmpty() + reminderKey
            prefs[KEY_SUBSCRIPTION_REMINDERS] = updated
        }
    }

    override suspend fun wasWeeklyReportNotified(periodKey: String): Boolean =
        readSet(KEY_WEEKLY_REPORTS).contains(periodKey)

    override suspend fun markWeeklyReportNotified(periodKey: String) {
        context.notificationPrefsDataStore.edit { prefs ->
            val updated = prefs[KEY_WEEKLY_REPORTS].orEmpty() + periodKey
            prefs[KEY_WEEKLY_REPORTS] = updated
        }
    }

    private suspend fun readSet(key: androidx.datastore.preferences.core.Preferences.Key<Set<String>>): Set<String> =
        context.notificationPrefsDataStore.data.map { it[key].orEmpty() }.first()

    private companion object {
        val KEY_SUBSCRIPTION_REMINDERS = stringSetPreferencesKey("subscription_reminder_keys")
        val KEY_WEEKLY_REPORTS = stringSetPreferencesKey("weekly_report_period_keys")
    }
}
