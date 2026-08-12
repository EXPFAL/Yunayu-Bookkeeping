package com.expfal.yunayu.domain.model

/** 预算阶段：常规 / 考试周 / 假期，用于切换预算策略。 */
enum class BudgetPhase {
    NORMAL,
    EXAM_WEEK,
    VACATION,
}

/**
 * 学期预算快照（PRD P0-2）。金额一律以「分」为单位。
 *
 * `weeklyQuota` / `monthlyQuota` 不落库，由学期信息与已花费聚合实时推导。
 */
data class BudgetSnapshot(
    val totalBudgetCents: Long,
    val spentCents: Long,
    val remainingCents: Long,
    val remainingDays: Int,
    val weeklyQuotaCents: Long,
    val monthlyQuotaCents: Long,
    val phase: BudgetPhase,
)
