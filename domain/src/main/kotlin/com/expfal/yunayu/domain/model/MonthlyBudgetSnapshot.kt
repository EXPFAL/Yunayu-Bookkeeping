package com.expfal.yunayu.domain.model

/**
 * 月度预算快照。金额一律以「分」为单位（Long），`remainingDays` 为含今天的剩余天数。
 *
 * 快照由 [com.expfal.yunayu.domain.usecase.MonthlyBudgetEngine] 实时推导，不落库。
 */
data class MonthlyBudgetSnapshot(
    val monthlyBudgetCents: Long,
    val spentCents: Long,
    val remainingCents: Long,
    val remainingDays: Int,
    val weeklyQuotaCents: Long,
)
