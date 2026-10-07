package com.expfal.yunayu.domain.report

import com.expfal.yunayu.domain.report.model.ReportPeriodType
import com.expfal.yunayu.domain.report.model.ReportSeriesSnapshot
import com.expfal.yunayu.domain.repository.TransactionRepository

/**
 * 打开报告详情时现算近 N 期序列 / 环比 / 分类增减（不落库）。
 */
class LoadReportSeriesUseCase(
    private val transactionRepository: TransactionRepository,
) {

    suspend operator fun invoke(
        periodType: ReportPeriodType,
        periodKey: String,
        seriesLength: Int = ReportSeriesCalculator.DEFAULT_SERIES_LENGTH,
    ): ReportSeriesSnapshot {
        if (periodType == ReportPeriodType.ANNUAL) {
            return ReportSeriesCalculator.snapshot(
                points = emptyList(),
                currentCategories = emptyList(),
                previousCategories = emptyList(),
            )
        }
        val windows = ReportSeriesCalculator.recentWindows(periodType, periodKey, seriesLength)
        val totals = windows.map { window ->
            transactionRepository.getWindowTotals(
                window.startInclusiveMs,
                window.endExclusiveMs,
            )
        }
        val points = ReportSeriesCalculator.toPoints(windows, totals)
        val currentWindow = windows.last()
        val previousWindow = windows.getOrNull(windows.lastIndex - 1)
        val currentCategories = transactionRepository.getExpenseByCategory(
            currentWindow.startInclusiveMs,
            currentWindow.endExclusiveMs,
        )
        val previousCategories = if (previousWindow != null) {
            transactionRepository.getExpenseByCategory(
                previousWindow.startInclusiveMs,
                previousWindow.endExclusiveMs,
            )
        } else {
            emptyList()
        }
        return ReportSeriesCalculator.snapshot(points, currentCategories, previousCategories)
    }
}
