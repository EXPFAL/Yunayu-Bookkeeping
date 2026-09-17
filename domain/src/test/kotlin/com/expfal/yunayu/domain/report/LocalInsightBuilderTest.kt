package com.expfal.yunayu.domain.report

import com.expfal.yunayu.domain.model.WindowTotals
import com.expfal.yunayu.domain.report.model.CategoryShare
import com.expfal.yunayu.domain.report.model.LocalInsightKind
import com.expfal.yunayu.domain.report.model.ReportPeriodType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** [LocalInsightBuilder] 纯函数单元测试。 */
class LocalInsightBuilderTest {

    @Test
    fun `empty window yields summary only`() {
        val insights = LocalInsightBuilder.build(
            periodType = ReportPeriodType.MONTHLY,
            totals = WindowTotals(0L, 0L),
            prevTotals = WindowTotals(0L, 0L),
            topCategories = emptyList(),
            uncategorizedCount = 0,
        )
        assertEquals(1, insights.size)
        assertEquals(LocalInsightKind.SUMMARY, insights.single().kind)
        assertTrue(insights.single().title.contains("暂无收支"))
    }

    @Test
    fun `negative net yields summary insight`() {
        val insights = LocalInsightBuilder.build(
            periodType = ReportPeriodType.MONTHLY,
            totals = WindowTotals(incomeCents = 1_000L, expenseCents = 2_000L),
            prevTotals = WindowTotals(0L, 0L),
            topCategories = emptyList(),
            uncategorizedCount = 0,
        )
        assertTrue(insights.any { it.kind == LocalInsightKind.SUMMARY && it.title.contains("净结余") })
    }

    @Test
    fun `heavy top category yields structure insight`() {
        val insights = LocalInsightBuilder.build(
            periodType = ReportPeriodType.MONTHLY,
            totals = WindowTotals(incomeCents = 0L, expenseCents = 10_000L),
            prevTotals = WindowTotals(0L, 0L),
            topCategories = listOf(CategoryShare("餐饮", 5_000L, 50, tagId = 1L)),
            uncategorizedCount = 0,
        )
        assertTrue(insights.any { it.kind == LocalInsightKind.STRUCTURE && it.title.contains("餐饮") })
    }

    @Test
    fun `uncategorized count yields data quality insight`() {
        val insights = LocalInsightBuilder.build(
            periodType = ReportPeriodType.WEEKLY,
            totals = WindowTotals(1_000L, 1_000L),
            prevTotals = WindowTotals(0L, 0L),
            topCategories = emptyList(),
            uncategorizedCount = 3,
        )
        assertTrue(insights.any { it.kind == LocalInsightKind.DATA_QUALITY && it.title.contains("3") })
    }

    @Test
    fun `large txn yields anomaly insight`() {
        val insights = LocalInsightBuilder.build(
            periodType = ReportPeriodType.MONTHLY,
            totals = WindowTotals(0L, 50_000L),
            prevTotals = WindowTotals(0L, 0L),
            topCategories = emptyList(),
            uncategorizedCount = 0,
            largeTxnCents = 25_000L,
            windowDayCount = 7,
        )
        assertTrue(insights.any { it.kind == LocalInsightKind.ANOMALY })
    }

    @Test
    fun `expense mom rise above threshold yields trend`() {
        val insights = LocalInsightBuilder.build(
            periodType = ReportPeriodType.MONTHLY,
            totals = WindowTotals(0L, 20_000L),
            prevTotals = WindowTotals(0L, 10_000L),
            topCategories = emptyList(),
            uncategorizedCount = 0,
            windowDayCount = 30,
        )
        assertTrue(insights.any { it.kind == LocalInsightKind.TREND && it.title.contains("支出") })
    }
}
