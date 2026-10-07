package com.expfal.yunayu.domain.report

import com.expfal.yunayu.domain.model.CategoryExpense
import com.expfal.yunayu.domain.model.WindowTotals
import com.expfal.yunayu.domain.report.model.CategoryChange
import com.expfal.yunayu.domain.report.model.MomComparison
import com.expfal.yunayu.domain.report.model.PeriodTotalsPoint
import com.expfal.yunayu.domain.report.model.ReportPeriodType
import com.expfal.yunayu.domain.report.model.ReportSeriesSnapshot
import com.expfal.yunayu.domain.util.TimeWindow
import com.expfal.yunayu.domain.util.TimeWindows
import kotlin.math.abs

/**
 * 报告近 N 期序列 / 环比 / 分类增减的纯函数工具。
 */
object ReportSeriesCalculator {

    const val DEFAULT_SERIES_LENGTH = 6
    const val TOP_CATEGORY_CHANGES = 3

    /**
     * 以 [periodKey] 为最新一期，向前取至多 [count] 个窗口（最旧在前）。
     * 年报不扩展序列，返回空列表。
     */
    fun recentWindows(
        periodType: ReportPeriodType,
        periodKey: String,
        count: Int = DEFAULT_SERIES_LENGTH,
    ): List<TimeWindow> {
        require(count >= 1)
        return when (periodType) {
            ReportPeriodType.ANNUAL -> emptyList()
            ReportPeriodType.WEEKLY -> {
                val current = TimeWindows.weekWindowByKey(periodKey)
                buildList(count) {
                    var key = current.periodKey
                    add(current)
                    repeat(count - 1) {
                        val prev = TimeWindows.previousWeekWindowByKey(key)
                        add(0, prev)
                        key = prev.periodKey
                    }
                }
            }
            ReportPeriodType.MONTHLY -> {
                val current = TimeWindows.monthWindowByKey(periodKey)
                buildList(count) {
                    var key = current.periodKey
                    add(current)
                    repeat(count - 1) {
                        val prev = TimeWindows.previousMonthWindowByKey(key)
                        add(0, prev)
                        key = prev.periodKey
                    }
                }
            }
        }
    }

    /** 由各期 totals（与 [windows] 顺序一致）组装时间点列表。 */
    fun toPoints(
        windows: List<TimeWindow>,
        totals: List<WindowTotals>,
    ): List<PeriodTotalsPoint> {
        require(windows.size == totals.size)
        return windows.zip(totals) { window, t ->
            PeriodTotalsPoint(
                periodKey = window.periodKey,
                incomeCents = t.incomeCents,
                expenseCents = t.expenseCents,
            )
        }
    }

    /** 列表最后一项为本期、倒数第二为上期；不足两期时差额为 0。 */
    fun momOf(points: List<PeriodTotalsPoint>): MomComparison {
        if (points.isEmpty()) {
            return MomComparison(0L, 0L, 0L, null)
        }
        val current = points.last()
        val previous = points.getOrNull(points.lastIndex - 1)
            ?: return MomComparison(
                incomeDeltaCents = current.incomeCents,
                expenseDeltaCents = current.expenseCents,
                netDeltaCents = current.netCents,
                expenseDeltaPercent = null,
            )
        val expenseDelta = current.expenseCents - previous.expenseCents
        val percent = if (previous.expenseCents == 0L) {
            null
        } else {
            ((expenseDelta * 100.0) / previous.expenseCents).toInt()
        }
        return MomComparison(
            incomeDeltaCents = current.incomeCents - previous.incomeCents,
            expenseDeltaCents = expenseDelta,
            netDeltaCents = current.netCents - previous.netCents,
            expenseDeltaPercent = percent,
        )
    }

    /**
     * 按金额变化绝对值排序，取 Top 增 / Top 减各 [limit] 条。
     * [tagName] 为 null 表示未分类。
     */
    fun categoryChanges(
        current: List<CategoryExpense>,
        previous: List<CategoryExpense>,
        limit: Int = TOP_CATEGORY_CHANGES,
    ): Pair<List<CategoryChange>, List<CategoryChange>> {
        val prevMap = previous.associateBy { it.tagName }
        val currMap = current.associateBy { it.tagName }
        val keys = (prevMap.keys + currMap.keys).toSet()
        val all = keys.map { name ->
            CategoryChange(
                tagName = name,
                currentCents = currMap[name]?.cents ?: 0L,
                previousCents = prevMap[name]?.cents ?: 0L,
            )
        }.filter { it.deltaCents != 0L }

        val increases = all
            .filter { it.deltaCents > 0L }
            .sortedByDescending { it.deltaCents }
            .take(limit)
        val decreases = all
            .filter { it.deltaCents < 0L }
            .sortedBy { it.deltaCents }
            .take(limit)
        return increases to decreases
    }

    /** 组装完整快照。 */
    fun snapshot(
        points: List<PeriodTotalsPoint>,
        currentCategories: List<CategoryExpense>,
        previousCategories: List<CategoryExpense>,
    ): ReportSeriesSnapshot {
        val (increases, decreases) = categoryChanges(currentCategories, previousCategories)
        return ReportSeriesSnapshot(
            points = points,
            mom = momOf(points),
            topIncreases = increases,
            topDecreases = decreases,
        )
    }

    /** 本期支出是否为近 N 期最大值或最小值（用于新鲜度二值）。 */
    fun isExpenseExtreme(points: List<PeriodTotalsPoint>): Boolean {
        if (points.size < 2) return false
        val current = points.last().expenseCents
        val others = points.dropLast(1).map { it.expenseCents }
        if (others.all { it == current }) return false
        return current >= others.max() || current <= others.min()
    }

    /** 环比幅度绝对值相对分母的归一化（0..1 粗略）。 */
    fun normalizeDelta(delta: Long, baseline: Long): Double {
        val denom = abs(baseline).coerceAtLeast(1L).toDouble()
        return (abs(delta).toDouble() / denom).coerceIn(0.0, 2.0) / 2.0
    }
}
