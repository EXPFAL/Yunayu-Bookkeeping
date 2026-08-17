package com.expfal.yunayu.domain.report

import com.expfal.yunayu.domain.report.model.ReportPeriodType
import com.expfal.yunayu.domain.repository.ReportRepository
import com.expfal.yunayu.domain.util.TimeWindow
import com.expfal.yunayu.domain.util.TimeWindows
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate

/**
 * 应用启动时的报告补生成编排用例。
 *
 * 打开应用即检查上月月度报告是否已生成，缺则补生成；每年 1 月首开再补上年年度报告。最多生成
 * 3 份且串行执行；周期键已存在（SUCCESS 或 FAILED）即跳过，FAILED 不自动重试（由未来报告页
 * 手动重试）。[Mutex] 防重入；[CancellationException] 重抛，其余异常吞掉不阻主流程。
 */
class EnsureReportsUseCase(
    private val reportRepository: ReportRepository,
    private val generateReportUseCase: GenerateReportUseCase,
) {

    private val mutex = Mutex()

    /** 按 [today] 补生成缺失的上周周报、上月月报与（1 月时）上年年报。 */
    suspend fun ensure(today: LocalDate) = mutex.withLock {
        ensureMonthly(today)
        ensureAnnual(today)
        ensureWeekly(today)
    }

    /** 上周周报缺失则生成（环比基期为上上周）。 */
    private suspend fun ensureWeekly(today: LocalDate) {
        val window = TimeWindows.previousWeekWindow(today)
        if (reportRepository.getByKey(ReportPeriodType.WEEKLY, window.periodKey) != null) return
        val prevWindow = TimeWindows.previousWeekWindow(today.minusWeeks(1))
        generateSafely(ReportPeriodType.WEEKLY, window, prevWindow)
    }

    /** 上月月报缺失则生成（环比基期为上上月）。 */
    private suspend fun ensureMonthly(today: LocalDate) {
        val window = TimeWindows.previousMonthWindow(today)
        if (reportRepository.getByKey(ReportPeriodType.MONTHLY, window.periodKey) != null) return
        val prevWindow = TimeWindows.previousMonthWindow(today.minusMonths(1))
        generateSafely(ReportPeriodType.MONTHLY, window, prevWindow)
    }

    /** 仅 1 月补上年年报（环比基期为再上一年）。 */
    private suspend fun ensureAnnual(today: LocalDate) {
        if (today.monthValue != 1) return
        val window = TimeWindows.previousYearWindow(today)
        if (reportRepository.getByKey(ReportPeriodType.ANNUAL, window.periodKey) != null) return
        val prevWindow = TimeWindows.previousYearWindow(today.minusYears(1))
        generateSafely(ReportPeriodType.ANNUAL, window, prevWindow)
    }

    /** 生成失败（含 DB / 引擎异常）不阻主流程；仅取消异常重抛。 */
    private suspend fun generateSafely(
        periodType: ReportPeriodType,
        window: TimeWindow,
        prevWindow: TimeWindow,
    ) {
        runCatching {
            generateReportUseCase(
                periodType = periodType,
                periodKey = window.periodKey,
                windowStartMs = window.startInclusiveMs,
                windowEndMs = window.endExclusiveMs,
                prevWindowStartMs = prevWindow.startInclusiveMs,
                prevWindowEndMs = prevWindow.endExclusiveMs,
            )
        }.onFailure { if (it is CancellationException) throw it }
    }
}
