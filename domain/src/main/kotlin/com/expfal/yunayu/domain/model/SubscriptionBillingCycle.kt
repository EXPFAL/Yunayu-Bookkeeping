package com.expfal.yunayu.domain.model

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** 订阅计费周期。 */
enum class SubscriptionBillingCycle {
    MONTHLY,
    QUARTERLY,
    YEARLY,
}

/** 将单次扣费金额折算为月均摊（分），用于年度/季度订阅的预算视角。 */
fun SubscriptionBillingCycle.monthlyAmortizedCents(amountCents: Long): Long = when (this) {
    SubscriptionBillingCycle.MONTHLY -> amountCents
    SubscriptionBillingCycle.QUARTERLY -> amountCents / 3
    SubscriptionBillingCycle.YEARLY -> amountCents / 12
}

/** 面向 UI 的周期文案。 */
fun SubscriptionBillingCycle.displayLabel(): String = when (this) {
    SubscriptionBillingCycle.MONTHLY -> "每月"
    SubscriptionBillingCycle.QUARTERLY -> "每季"
    SubscriptionBillingCycle.YEARLY -> "每年"
}

/** 从某一扣费日推进到下一期（本地日历，保留日粒度）。 */
fun SubscriptionBillingCycle.advanceDueAt(currentDueAt: Long, zoneId: ZoneId = ZoneId.systemDefault()): Long {
    val date = Instant.ofEpochMilli(currentDueAt).atZone(zoneId).toLocalDate()
    val next = when (this) {
        SubscriptionBillingCycle.MONTHLY -> date.plusMonths(1)
        SubscriptionBillingCycle.QUARTERLY -> date.plusMonths(3)
        SubscriptionBillingCycle.YEARLY -> date.plusYears(1)
    }
    return next.atStartOfDay(zoneId).toInstant().toEpochMilli()
}

/**
 * 由起始日与上一笔已记扣费日，推算下一期待记扣费日。
 * 从未记过时返回 [billingStartAt]；否则自起始日按周期前进，跳过已记期。
 */
fun SubscriptionBillingCycle.nextUnpostedChargeDueAt(
    billingStartAt: Long,
    lastPostedDueAt: Long?,
    zoneId: ZoneId = ZoneId.systemDefault(),
): Long {
    if (lastPostedDueAt == null) return billingStartAt
    var due = billingStartAt
    while (due <= lastPostedDueAt) {
        due = advanceDueAt(due, zoneId)
    }
    return due
}

/** 新建订阅时的默认起始日：当天 0 点（本地时区）。 */
fun initialBillingStartAt(zoneId: ZoneId = ZoneId.systemDefault()): Long =
    LocalDate.now(zoneId).atStartOfDay(zoneId).toInstant().toEpochMilli()

/** @deprecated 使用 [initialBillingStartAt] */
@Deprecated("Renamed to initialBillingStartAt", ReplaceWith("initialBillingStartAt(zoneId)"))
fun initialSubscriptionDueAt(zoneId: ZoneId = ZoneId.systemDefault()): Long = initialBillingStartAt(zoneId)
