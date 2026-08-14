package com.expfal.yunayu.domain.usecase

import com.expfal.yunayu.domain.model.MonthlyBudgetSnapshot
import com.expfal.yunayu.domain.repository.MonthlyBudgetRepository
import com.expfal.yunayu.domain.repository.TransactionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * [MonthlyBudgetEngine] 的纯函数实现，仅依赖领域仓储接口，无框架注解。
 *
 * 算法与假设（详见各方法 KDoc）：
 * 1. 「当月」= `today` 所在的自然月，支出窗口为 `[当月 1 日 00:00, 下月 1 日 00:00)`，
 *    端点取系统默认时区的当日零点毫秒。
 * 2. `spentCents` 直接复用 [TransactionRepository.observeExpenseSumBetween] 的
 *    支出聚合结果（已排除收入，见其 KDoc）。
 * 3. `remainingCents = (月度预算 - 已花费).coerceAtLeast(0)`。
 * 4. `remainingDays = 距月末天数 + 1`（含今天），并钳制到至少 `1` 以防除零。
 * 5. `weeklyQuotaCents = remainingCents * 7 / remainingDays`，整数除法向下取整。
 * 6. 预算未设置时仓储发射 `0`，引擎照常产出 `spentCents` 正常的快照，由 UI 转译为引导态。
 */
class MonthlyBudgetEngineImpl(
    private val monthlyBudgetRepository: MonthlyBudgetRepository,
    private val transactionRepository: TransactionRepository,
) : MonthlyBudgetEngine {

    /**
     * 观察 `today` 视角下的月度预算快照。
     *
     * 由 [combine]（月度预算流 + 当月支出聚合流）实时推导，任一数据源变更即重新发射。
     */
    override fun observeSnapshot(today: LocalDate): Flow<MonthlyBudgetSnapshot> =
        combine(
            monthlyBudgetRepository.observeMonthlyBudgetCents(),
            transactionRepository.observeExpenseSumBetween(
                startInclusiveMs = monthStartMillis(today),
                endExclusiveMs = nextMonthStartMillis(today),
            ),
        ) { budgetCents, spentCents ->
            buildSnapshot(budgetCents, spentCents, today)
        }

    /** 由月度预算、当月已花费与 `today` 组装快照。 */
    private fun buildSnapshot(
        budgetCents: Long,
        spentCents: Long,
        today: LocalDate,
    ): MonthlyBudgetSnapshot {
        val remainingCents = (budgetCents - spentCents).coerceAtLeast(0L)
        val remainingDays =
            (ChronoUnit.DAYS.between(today, monthEnd(today)).toInt() + 1)
                .coerceAtLeast(MIN_REMAINING_DAYS)
        return MonthlyBudgetSnapshot(
            monthlyBudgetCents = budgetCents,
            spentCents = spentCents,
            remainingCents = remainingCents,
            remainingDays = remainingDays,
            weeklyQuotaCents = remainingCents * DAYS_PER_WEEK / remainingDays,
        )
    }

    /** 当月 1 日 00:00（系统默认时区）对应的毫秒，作为支出窗口的含端点起点。 */
    private fun monthStartMillis(today: LocalDate): Long =
        today.withDayOfMonth(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

    /** 下月 1 日 00:00（系统默认时区）对应的毫秒，作为支出窗口的不含端终点。 */
    private fun nextMonthStartMillis(today: LocalDate): Long =
        monthEnd(today).plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

    /** `today` 所在月的最后一天。 */
    private fun monthEnd(today: LocalDate): LocalDate =
        today.withDayOfMonth(today.lengthOfMonth())

    private companion object {
        const val DAYS_PER_WEEK = 7
        const val MIN_REMAINING_DAYS = 1
    }
}
