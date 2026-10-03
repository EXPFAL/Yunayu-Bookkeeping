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
 * 打开应用即检查上月月报是否已生成，缺则补生成；随后补生成本周与本月报告（打开报告页即可看到当期汇总）。
 * 不再补年报。最多串行执行；周期键已存在（SUCCESS / FAILED / STALE）即跳过，FAILED / STALE 不自动重试
 * （由报告页手动重试）。[Mutex] 防重入；[CancellationException] 重抛，其余异常吞掉不阻主流程。
 */
class EnsureReportsUseCase(
    private val reportRepository: ReportRepository,
    private val generateReportUseCase: GenerateReportUseCase,
) {

    private val mutex = Mutex()

    /**
     * 按 [today] 补生成缺失的上周周报、上月月报，以及本周 / 本月当期报告。
     */
    suspend fun ensure(today: LocalDate) = mutex.withLock {
        ensureMonthly(today)
        ensureWeekly(today)
        ensureCurrentWeek(today)
        ensureCurrentMonth(today)
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

    /** 本周周报缺失则生成（环比基期为上周）。 */
    private suspend fun ensureCurrentWeek(today: LocalDate) {
        val window = TimeWindows.weekWindow(today)
        if (reportRepository.getByKey(ReportPeriodType.WEEKLY, window.periodKey) != null) return
        val prevWindow = TimeWindows.previousWeekWindow(today)
        generateSafely(ReportPeriodType.WEEKLY, window, prevWindow)
    }

    /** 本月月报缺失则生成（环比基期为上月）。 */
    private suspend fun ensureCurrentMonth(today: LocalDate) {
        val window = TimeWindows.monthWindow(today)
        if (reportRepository.getByKey(ReportPeriodType.MONTHLY, window.periodKey) != null) return
        val prevWindow = TimeWindows.previousMonthWindow(today)
        generateSafely(ReportPeriodType.MONTHLY, window, prevWindow)
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
