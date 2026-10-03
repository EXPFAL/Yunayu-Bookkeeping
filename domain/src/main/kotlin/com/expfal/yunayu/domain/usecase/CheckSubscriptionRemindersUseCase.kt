package com.expfal.yunayu.domain.usecase

import com.expfal.yunayu.domain.model.SUBSCRIPTION_REMINDER_DAYS_BEFORE
import com.expfal.yunayu.domain.model.Subscription
import com.expfal.yunayu.domain.repository.NotificationPreferencesRepository
import com.expfal.yunayu.domain.repository.SubscriptionRepository
import kotlinx.coroutines.flow.first

/** 待推送的订阅扣费提醒。 */
data class SubscriptionReminder(
    val subscriptionId: Long,
    val name: String,
    val chargeDueAt: Long,
    val reminderKey: String,
)

/**
 * 扫描生效订阅，返回距扣费日恰好 [SUBSCRIPTION_REMINDER_DAYS_BEFORE] 天、且尚未推送过的提醒项。
 */
class CheckSubscriptionRemindersUseCase(
    private val subscriptionRepository: SubscriptionRepository,
    private val notificationPreferencesRepository: NotificationPreferencesRepository,
) {

    suspend operator fun invoke(): List<SubscriptionReminder> {
        val subscriptions = subscriptionRepository.observeAll().first()
        return subscriptions
            .filter { it.isActive && it.daysUntilDue() == SUBSCRIPTION_REMINDER_DAYS_BEFORE }
            .mapNotNull { subscription -> subscription.toReminderOrNull() }
    }

    private suspend fun Subscription.toReminderOrNull(): SubscriptionReminder? {
        val dueAt = nextChargeDueAt()
        val key = reminderKey(id, dueAt)
        if (notificationPreferencesRepository.wasSubscriptionReminderSent(key)) return null
        return SubscriptionReminder(
            subscriptionId = id,
            name = name,
            chargeDueAt = dueAt,
            reminderKey = key,
        )
    }

    companion object {
        fun reminderKey(subscriptionId: Long, chargeDueAt: Long): String = "sub_${subscriptionId}_$chargeDueAt"
    }
}
