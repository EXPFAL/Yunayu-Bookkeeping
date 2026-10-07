package com.expfal.yunayu.domain.report

import com.expfal.yunayu.domain.report.model.CategoryShare
import com.expfal.yunayu.domain.report.model.LocalInsight
import com.expfal.yunayu.domain.report.model.LocalInsightKind
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
 * 链路：聚合当期/上期 + Top 分类 → 近 N 期序列（供评分）→ 细节洞察 →
 * 建议 / 叙事 → 落库。结构化数据与本地洞察齐全即 [ReportStatus.SUCCESS]。
 * 重生成成功后清空 [Report.analysisText]（旧 AI 深读作废）。
 */
class GenerateReportUseCase(
    private val transactionRepository: TransactionRepository,
    private val reportRepository: ReportRepository,
    private val monthlyBudgetRepository: MonthlyBudgetRepository,
) {

    /**
     * 生成并持久化报告；[prevWindowStartMs]/[prevWindowEndMs] 为环比基期窗口（半开区间）。
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
        val windowTransactions = transactionRepository.getBetween(windowStartMs, windowEndMs)

        val zone = ZoneId.systemDefault()
        val today = Instant.ofEpochMilli(generatedAtMs).atZone(zone).toLocalDate()
        val budgetCents = monthlyBudgetRepository.observeMonthlyBudgetCents().first()
        val monthStart = TimeWindows.monthStartMillis(today)
        val monthEnd = TimeWindows.nextMonthStartMillis(today)
        val spentInMonth = transactionRepository.getWindowTotals(monthStart, monthEnd).expenseCents
        val daysInMonth = today.lengthOfMonth()
        val elapsedDays = today.dayOfMonth

        val detailInsights = LocalInsightBuilder.build(
            periodType = periodType,
            totals = totals,
            topCategories = topCategories,
            uncategorizedCount = uncategorizedCount,
            budgetCents = budgetCents,
            spentInBudgetMonthCents = spentInMonth,
            elapsedDaysInMonth = elapsedDays,
            daysInMonth = daysInMonth,
            largeTxnCents = largeTxn,
            windowTransactions = windowTransactions,
        )

        val series = loadSeriesForScoring(periodType, periodKey)
        val advice = ReportAdviceBuilder.build(
            totals = totals,
            topCategories = topCategories,
            topIncreases = series.topIncreases,
            uncategorizedCount = uncategorizedCount,
            budgetCents = budgetCents,
            spentInBudgetMonthCents = spentInMonth,
            elapsedDaysInMonth = elapsedDays,
            daysInMonth = daysInMonth,
        )
        val scored = ReportFactScorer.score(
            totals = totals,
            mom = series.mom,
            points = series.points,
            topCategories = topCategories,
            detailInsights = detailInsights,
            series = series,
        )
        val story = ReportNarrativeBuilder.build(
            totals = totals,
            scoredFacts = scored,
            adviceTitles = advice.map { it.title },
        )
        val localInsights = composeInsights(story, advice, detailInsights)

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

    private suspend fun loadSeriesForScoring(
        periodType: ReportPeriodType,
        periodKey: String,
    ) = when (periodType) {
        ReportPeriodType.ANNUAL ->
            ReportSeriesCalculator.snapshot(
                points = emptyList(),
                currentCategories = emptyList(),
                previousCategories = emptyList(),
            )
        else -> LoadReportSeriesUseCase(transactionRepository)(periodType, periodKey)
    }

    private fun composeInsights(
        story: LocalInsight,
        advice: List<LocalInsight>,
        detailInsights: List<LocalInsight>,
    ): List<LocalInsight> {
        val details = detailInsights.filter {
            it.kind != LocalInsightKind.STORY && it.kind != LocalInsightKind.ADVICE
        }
        return buildList {
            add(story)
            addAll(advice)
            addAll(details)
        }
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
                    percent = if (expenseCents > 0L) {
                        (it.cents * 100 / expenseCents).toInt()
                    } else {
                        0
                    },
                    tagId = it.tagId,
                )
            }
}
