package com.expfal.yunayu.domain.model

/**
 * 月度预算快照。金额一律以「分」为单位（Long），`remainingDays` 为含今天的剩余天数。
 *
 * 快照由 [com.expfal.yunayu.domain.usecase.MonthlyBudgetEngine] 实时推导，不落库。
 * `weeklyQuotaCents` 是本周（与本月相交段）在周起点冻结的额度；
 * `weeklyRemainingCents` 为本周还可花（额度减去本周本月支出，下限 0）。
 */
data class MonthlyBudgetSnapshot(
    val monthlyBudgetCents: Long,
    val spentCents: Long,
    val remainingCents: Long,
    val remainingDays: Int,
    val weeklyQuotaCents: Long,
    val spentThisWeekCents: Long,
    val weeklyRemainingCents: Long,
)
