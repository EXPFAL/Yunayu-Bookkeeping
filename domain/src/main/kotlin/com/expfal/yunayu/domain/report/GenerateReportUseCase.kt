package com.expfal.yunayu.domain.report

import com.expfal.yunayu.domain.report.model.CategoryShare
import com.expfal.yunayu.domain.report.model.Report
import com.expfal.yunayu.domain.report.model.ReportPeriodType
import com.expfal.yunayu.domain.report.model.ReportStatus
import com.expfal.yunayu.domain.repository.MonthlyBudgetRepository
import com.expfal.yunayu.domain.repository.ReportRepository
import com.expfal.yunayu.domain.repository.TransactionRepository
import com.expfal.yunayu.domain.util.TimeWindows
import kotlinx.coroutines.flow.first
import java.time.Instant
import java.time.ZoneId

/**
 * 生成一份周期报告的编排用例。
 *
 * 链路：聚合当期/上期 + Top 分类 → 本地洞察 → 落库。
 * 结构化数据与本地洞察齐全即 [ReportStatus.SUCCESS]。
 */
class GenerateReportUseCase(
    private val transactionRepository: TransactionRepository,
    private val reportRepository: ReportRepository,
    private val monthlyBudgetRepository: MonthlyBudgetRepository,
) {

    /**
     * 生成并持久化报告；[prevWindowStartMs]/[prevWindowEndMs] 为环比基期窗口（半开区间）。
     * 上期金额仍写入报告行，供以后需要时使用；界面不再单独展示环比。
     */
    suspend operator fun invoke(
        periodType: ReportPeriodType,
        periodKey: String,
        windowStartMs: Long,
        windowEndMs: Long,
        prevWindowStartMs: Long,
        prevWindowEndMs: Long,
        generatedAtMs: Long = System.currentTimeMillis(),
    ) {
        val totals = transactionRepository.getWindowTotals(windowStartMs, windowEndMs)
        val prevTotals = transactionRepository.getWindowTotals(prevWindowStartMs, prevWindowEndMs)
        val topCategories = buildTopCategories(windowStartMs, windowEndMs, totals.expenseCents)
        val uncategorizedCount =
            transactionRepository.countUncategorizedBetween(windowStartMs, windowEndMs)
        val largeTxn = transactionRepository.getMaxExpenseCentsBetween(windowStartMs, windowEndMs)

        val zone = ZoneId.systemDefault()
        val today = Instant.ofEpochMilli(generatedAtMs).atZone(zone).toLocalDate()
        val budgetCents = monthlyBudgetRepository.observeMonthlyBudgetCents().first()
        val monthStart = TimeWindows.monthStartMillis(today)
        val monthEnd = TimeWindows.nextMonthStartMillis(today)
        val spentInMonth = transactionRepository.getWindowTotals(monthStart, monthEnd).expenseCents
        val daysInMonth = today.lengthOfMonth()
        val elapsedDays = today.dayOfMonth

        val localInsights = LocalInsightBuilder.build(
            periodType = periodType,
            totals = totals,
            topCategories = topCategories,
            uncategorizedCount = uncategorizedCount,
            budgetCents = budgetCents,
            spentInBudgetMonthCents = spentInMonth,
            elapsedDaysInMonth = elapsedDays,
            daysInMonth = daysInMonth,
            largeTxnCents = largeTxn,
        )

        val existingId = reportRepository.getByKey(periodType, periodKey)?.id ?: 0L
        reportRepository.upsert(
            Report(
                id = existingId,
                periodType = periodType,
                periodKey = periodKey,
                windowStartMs = windowStartMs,
                windowEndMs = windowEndMs,
                incomeCents = totals.incomeCents,
                expenseCents = totals.expenseCents,
                topCategories = topCategories,
                prevIncomeCents = prevTotals.incomeCents,
                prevExpenseCents = prevTotals.expenseCents,
                localInsights = localInsights,
                analysisText = null,
                status = ReportStatus.SUCCESS,
                generatedAtMs = generatedAtMs,
            ),
        )
    }

    private suspend fun buildTopCategories(
        windowStartMs: Long,
        windowEndMs: Long,
        expenseCents: Long,
    ): List<CategoryShare> =
        transactionRepository.getExpenseByCategory(windowStartMs, windowEndMs)
            .take(ReportCategoryLimits.TOP_CATEGORIES)
            .map {
                CategoryShare(
                    tagName = it.tagName,
                    cents = it.cents,
                    percent = percentOf(it.cents, expenseCents),
                    tagId = it.tagId,
                )
            }

    private fun percentOf(partCents: Long, totalCents: Long): Int =
        if (totalCents <= 0L) 0 else ((partCents * 100) / totalCents).toInt()
}
