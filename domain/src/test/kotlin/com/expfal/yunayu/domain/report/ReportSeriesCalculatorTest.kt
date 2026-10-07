package com.expfal.yunayu.domain.report

import com.expfal.yunayu.domain.model.CategoryExpense
import com.expfal.yunayu.domain.model.WindowTotals
import com.expfal.yunayu.domain.report.model.PeriodTotalsPoint
import com.expfal.yunayu.domain.report.model.ReportPeriodType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ReportSeriesCalculatorTest {

    @Test
    fun `recent monthly windows oldest first length 6`() {
        val windows = ReportSeriesCalculator.recentWindows(
            ReportPeriodType.MONTHLY,
            "2026-07",
            count = 6,
        )
        assertEquals(6, windows.size)
        assertEquals("2026-02", windows.first().periodKey)
        assertEquals("2026-07", windows.last().periodKey)
    }

    @Test
    fun `recent weekly windows oldest first`() {
        val windows = ReportSeriesCalculator.recentWindows(
            ReportPeriodType.WEEKLY,
            "2026-W34",
            count = 3,
        )
        assertEquals(listOf("2026-W32", "2026-W33", "2026-W34"), windows.map { it.periodKey })
    }

    @Test
    fun `annual series windows empty`() {
        assertTrue(
            ReportSeriesCalculator.recentWindows(ReportPeriodType.ANNUAL, "2026").isEmpty(),
        )
    }

    @Test
    fun `mom and category changes`() {
        val points = listOf(
            PeriodTotalsPoint("2026-06", 4_000L, 2_500L),
            PeriodTotalsPoint("2026-07", 5_000L, 3_500L),
        )
        val mom = ReportSeriesCalculator.momOf(points)
        assertEquals(1_000L, mom.incomeDeltaCents)
        assertEquals(1_000L, mom.expenseDeltaCents)
        assertEquals(0L, mom.netDeltaCents)
        assertEquals(40, mom.expenseDeltaPercent)

        val (inc, dec) = ReportSeriesCalculator.categoryChanges(
            current = listOf(
                CategoryExpense("餐饮", 2_000L),
                CategoryExpense("交通", 100L),
            ),
            previous = listOf(
                CategoryExpense("餐饮", 500L),
                CategoryExpense("交通", 800L),
            ),
        )
        assertEquals("餐饮", inc.single().tagName)
        assertEquals(1_500L, inc.single().deltaCents)
        assertEquals("交通", dec.single().tagName)
        assertEquals(-700L, dec.single().deltaCents)
    }

    @Test
    fun `expense extreme detection`() {
        val points = listOf(
            PeriodTotalsPoint("a", 0, 100),
            PeriodTotalsPoint("b", 0, 200),
            PeriodTotalsPoint("c", 0, 500),
        )
        assertTrue(ReportSeriesCalculator.isExpenseExtreme(points))
        assertFalse(
            ReportSeriesCalculator.isExpenseExtreme(
                listOf(
                    PeriodTotalsPoint("a", 0, 100),
                    PeriodTotalsPoint("b", 0, 100),
                ),
            ),
        )
    }

    @Test
    fun `toPoints zips windows and totals`() {
        val windows = ReportSeriesCalculator.recentWindows(ReportPeriodType.MONTHLY, "2026-07", 2)
        val points = ReportSeriesCalculator.toPoints(
            windows,
            listOf(WindowTotals(1, 2), WindowTotals(3, 4)),
        )
        assertEquals("2026-06", points[0].periodKey)
        assertEquals(4L, points[1].expenseCents)
    }
}
