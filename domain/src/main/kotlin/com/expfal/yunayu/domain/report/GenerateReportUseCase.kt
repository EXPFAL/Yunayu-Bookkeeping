package com.expfal.yunayu.domain.report

import com.expfal.yunayu.domain.model.WindowTotals
import com.expfal.yunayu.domain.report.model.CategoryShare
import com.expfal.yunayu.domain.report.model.Report
import com.expfal.yunayu.domain.report.model.ReportPeriodType
import com.expfal.yunayu.domain.report.model.ReportStatus
import com.expfal.yunayu.domain.repository.MonthlyBudgetRepository
import com.expfal.yunayu.domain.repository.ReportRepository
import com.expfal.yunayu.domain.repository.TransactionRepository
import com.expfal.yunayu.domain.util.TimeWindows
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * 生成一份周期报告的编排用例。
 *
 * 链路：聚合当期/上期 + Top 分类 → 本地洞察（必写）→ 可选 LLM 点评 → 落库。
 * 无 API / 分析失败时仍 [ReportStatus.SUCCESS]（结构化 + 本地洞察齐全）；仅取消异常上抛。
 */
class GenerateReportUseCase(
    private val transactionRepository: TransactionRepository,
    private val reportRepository: ReportRepository,
    private val analyzer: ReportAnalyzer,
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

        val zone = ZoneId.systemDefault()
        val today = Instant.ofEpochMilli(generatedAtMs).atZone(zone).toLocalDate()
        val budgetCents = monthlyBudgetRepository.observeMonthlyBudgetCents().first()
        val monthStart = TimeWindows.monthStartMillis(today)
        val monthEnd = TimeWindows.nextMonthStartMillis(today)
        val spentInMonth = transactionRepository.getWindowTotals(monthStart, monthEnd).expenseCents
        val daysInMonth = today.lengthOfMonth()
        val elapsedDays = today.dayOfMonth
        val windowDays = ChronoUnit.DAYS.between(
            Instant.ofEpochMilli(windowStartMs).atZone(zone).toLocalDate(),
            Instant.ofEpochMilli(windowEndMs).atZone(zone).toLocalDate(),
        ).toInt().coerceAtLeast(1)

        val localInsights = LocalInsightBuilder.build(
            periodType = periodType,
            totals = totals,
            prevTotals = prevTotals,
            topCategories = topCategories,
            uncategorizedCount = uncategorizedCount,
            budgetCents = budgetCents,
            spentInBudgetMonthCents = spentInMonth,
            elapsedDaysInMonth = elapsedDays,
            daysInMonth = daysInMonth,
            largeTxnCents = largeTxn,
            windowDayCount = windowDays,
        )

        val analysisText = analyzeOptional(topCategories, totals, prevTotals, localInsights)
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
                analysisText = analysisText,
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
            .take(TOP_CATEGORIES_LIMIT)
            .map {
                CategoryShare(
                    tagName = it.tagName,
                    cents = it.cents,
                    percent = percentOf(it.cents, expenseCents),
                    tagId = it.tagId,
                )
            }

    /** LLM 可选：不可用 / 失败 / 超时返回 null，不影响报告 SUCCESS。 */
    private suspend fun analyzeOptional(
        topCategories: List<CategoryShare>,
        totals: WindowTotals,
        prevTotals: WindowTotals,
        localInsights: List<com.expfal.yunayu.domain.report.model.LocalInsight>,
    ): String? {
        if (!analyzer.isAvailable()) return null
        val instruction = ReportPromptBuilder.buildSystemInstruction()
        val dataText = ReportPromptBuilder.buildDataText(
            incomeCents = totals.incomeCents,
            expenseCents = totals.expenseCents,
            topCategories = topCategories,
            prevIncomeCents = prevTotals.incomeCents,
            prevExpenseCents = prevTotals.expenseCents,
            localInsightTitles = localInsights.map { it.title },
        )
        val raw = try {
            withTimeoutOrNull(ANALYZE_TIMEOUT_MILLIS) { analyzer.analyze(instruction, dataText) }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        } ?: return null
        return sanitizeAnalysis(raw)
    }

    private fun sanitizeAnalysis(raw: String): String? {
        var text = raw.trim()
        text = text.replace(Regex("^```[a-zA-Z]*\\s*"), "").trim()
        text = text.removeSuffix("```").trim()
        text = text.take(MAX_ANALYSIS_CHARS)
        if (text.isNotEmpty() && Character.isHighSurrogate(text.last())) {
            text = text.dropLast(1)
        }
        return text.takeIf { it.isNotBlank() }
    }

    private fun percentOf(partCents: Long, totalCents: Long): Int =
        if (totalCents <= 0L) 0 else ((partCents * 100) / totalCents).toInt()

    private companion object {
        const val TOP_CATEGORIES_LIMIT = 5
        const val ANALYZE_TIMEOUT_MILLIS = 65_000L
        const val MAX_ANALYSIS_CHARS = 2000
    }
}
