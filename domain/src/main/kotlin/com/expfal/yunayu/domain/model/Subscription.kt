package com.expfal.yunayu.domain.model

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * 长期订阅开支项（如流媒体、软件会员），用于总览与按月均摊预算。
 *
 * @property amountCents 单次扣费金额（分），与 [billingCycle] 组合表示实际账单节奏。
 * @property billingStartAt 订阅起始日 0 点毫秒时间戳，作为周期推算锚点。
 * @property lastPostedDueAt 上一笔已记对应的扣费日，可空表示从未记过。
 * @property lastPostedAt 最近一次记入交易的操作时间，可空。
 */
data class Subscription(
    val id: Long = 0L,
    val name: String,
    val amountCents: Long,
    val billingCycle: SubscriptionBillingCycle,
    val note: String? = null,
    val isActive: Boolean = true,
    val billingStartAt: Long = initialBillingStartAt(),
    val lastPostedDueAt: Long? = null,
    val lastPostedAt: Long? = null,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
) {
    val monthlyAmortizedCents: Long
        get() = billingCycle.monthlyAmortizedCents(amountCents)

    /** 下一期待记扣费日（由起始日与周期推算，不受用户直接编辑）。 */
    fun nextChargeDueAt(zoneId: ZoneId = ZoneId.systemDefault()): Long =
        billingCycle.nextUnpostedChargeDueAt(billingStartAt, lastPostedDueAt, zoneId)

    /** 当前窗口内最早待记扣费日；无待记项时返回 null。 */
    fun pendingChargeDueAt(
        windowDays: Long = DEFAULT_DUE_WINDOW_DAYS,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): Long? {
        if (!isActive) return null
        val due = nextChargeDueAt(zoneId)
        val windowEnd = LocalDate.now(zoneId).plusDays(windowDays)
            .atStartOfDay(zoneId).toInstant().toEpochMilli() + DAY_MS - 1
        return due.takeIf { it <= windowEnd }
    }

    /** 是否已过期（待记扣费日早于今天 0 点）。 */
    fun isOverdue(zoneId: ZoneId = ZoneId.systemDefault()): Boolean {
        if (!isActive) return false
        val startOfToday = LocalDate.now(zoneId).atStartOfDay(zoneId).toInstant().toEpochMilli()
        return nextChargeDueAt(zoneId) < startOfToday
    }

    /** 是否进入提醒窗口：已到期或 [windowDays] 天内即将到期。 */
    fun isDueWithin(windowDays: Long = DEFAULT_DUE_WINDOW_DAYS, zoneId: ZoneId = ZoneId.systemDefault()): Boolean =
        pendingChargeDueAt(windowDays, zoneId) != null

    /** 距下一期待记扣费日的整天数（负数表示已过期）。 */
    fun daysUntilDue(zoneId: ZoneId = ZoneId.systemDefault()): Long {
        val today = LocalDate.now(zoneId)
        val due = Instant.ofEpochMilli(nextChargeDueAt(zoneId)).atZone(zoneId).toLocalDate()
        return ChronoUnit.DAYS.between(today, due)
    }

    private companion object {
        const val DAY_MS = 86_400_000L
        const val DEFAULT_DUE_WINDOW_DAYS = 7L
    }
}

/** 订阅到期提醒默认窗口（天）。 */
const val SUBSCRIPTION_DUE_WINDOW_DAYS = 7L
