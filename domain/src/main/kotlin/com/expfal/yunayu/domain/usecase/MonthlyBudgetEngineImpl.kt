package com.expfal.yunayu.domain.usecase

import com.expfal.yunayu.domain.model.MonthlyBudgetSnapshot
import com.expfal.yunayu.domain.repository.MonthlyBudgetRepository
import com.expfal.yunayu.domain.repository.TransactionRepository
import com.expfal.yunayu.domain.util.TimeWindows
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * [MonthlyBudgetEngine] 的纯函数实现，仅依赖领域仓储接口，无框架注解。
 *
 * 算法与假设（详见各方法 KDoc）：
 * 1. 「当月」= `today` 所在的自然月，支出窗口为 `[当月 1 日 00:00, 下月 1 日 00:00)`。
 * 2. `spentCents` 复用 [TransactionRepository.observeExpenseSumBetween]（已排除收入）。
 * 3. `remainingCents = (月度预算 - 已花费).coerceAtLeast(0)`。
 * 4. `remainingDays = 距月末天数 + 1`（含今天），并钳制到至少 `1` 以防除零。
 * 5. 预算周 = 本周 ∩ 本月：`[max(周一, 月1日), min(下周一, 下月1日))`。
 * 6. `weeklyQuotaCents` 按周起点剩余冻结：
 *    `(月预算 - 周前本月支出) * 本周落在本月天数 / 周起点到月末天数`，整数除法。
 * 7. `weeklyRemainingCents = (weeklyQuotaCents - 本周本月支出).coerceAtLeast(0)`。
 * 8. 预算未设置时仓储发射 `0`，引擎照常产出快照，由 UI 转译为引导态。
 */
class MonthlyBudgetEngineImpl(
    private val monthlyBudgetRepository: MonthlyBudgetRepository,
    private val transactionRepository: TransactionRepository,
) : MonthlyBudgetEngine {

    /**
     * 观察 `today` 视角下的月度预算快照。
     *
     * 由 [combine]（月度预算流 + 当月支出 + 本周∩本月支出）实时推导。
     */
    override fun observeSnapshot(today: LocalDate): Flow<MonthlyBudgetSnapshot> =
        combine(
            monthlyBudgetRepository.observeMonthlyBudgetCents(),
            transactionRepository.observeExpenseSumBetween(
                startInclusiveMs = TimeWindows.monthStartMillis(today),
                endExclusiveMs = TimeWindows.nextMonthStartMillis(today),
            ),
            transactionRepository.observeExpenseSumBetween(
                startInclusiveMs = TimeWindows.budgetWeekStartMillis(today),
                endExclusiveMs = TimeWindows.budgetWeekEndExclusiveMillis(today),
            ),
        ) { budgetCents, spentCents, spentThisWeekCents ->
            buildSnapshot(budgetCents, spentCents, spentThisWeekCents, today)
        }

    /** 由月度预算、当月已花费、本周已花费与 `today` 组装快照。 */
    private fun buildSnapshot(
        budgetCents: Long,
        spentCents: Long,
        spentThisWeekCents: Long,
        today: LocalDate,
    ): MonthlyBudgetSnapshot {
        val remainingCents = (budgetCents - spentCents).coerceAtLeast(0L)
        val remainingDays =
            (ChronoUnit.DAYS.between(today, TimeWindows.monthEnd(today)).toInt() + 1)
                .coerceAtLeast(MIN_REMAINING_DAYS)
        val spentBeforeWeekCents = (spentCents - spentThisWeekCents).coerceAtLeast(0L)
        val remainingAtWeekStart = (budgetCents - spentBeforeWeekCents).coerceAtLeast(0L)
        val daysFromWeekStart = TimeWindows.daysInMonthFromBudgetWeekStart(today)
        val daysOfWeekInMonth = TimeWindows.daysOfWeekInMonth(today)
        val weeklyQuotaCents = remainingAtWeekStart * daysOfWeekInMonth / daysFromWeekStart
        val weeklyRemainingCents = (weeklyQuotaCents - spentThisWeekCents).coerceAtLeast(0L)
        return MonthlyBudgetSnapshot(
            monthlyBudgetCents = budgetCents,
            spentCents = spentCents,
            remainingCents = remainingCents,
            remainingDays = remainingDays,
            weeklyQuotaCents = weeklyQuotaCents,
            spentThisWeekCents = spentThisWeekCents,
            weeklyRemainingCents = weeklyRemainingCents,
        )
    }

    private companion object {
        const val MIN_REMAINING_DAYS = 1
    }
}
