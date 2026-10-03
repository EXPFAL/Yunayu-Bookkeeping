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
            topCategories = emptyList(),
            uncategorizedCount = 0,
            largeTxnCents = 25_000L,
        )
        assertTrue(insights.any { it.kind == LocalInsightKind.ANOMALY })
    }

    @Test
    fun `budget pace ahead yields budget insight`() {
        val insights = LocalInsightBuilder.build(
            periodType = ReportPeriodType.MONTHLY,
            totals = WindowTotals(0L, 8_000L),
            topCategories = emptyList(),
            uncategorizedCount = 0,
            budgetCents = 10_000L,
            spentInBudgetMonthCents = 8_000L,
            elapsedDaysInMonth = 5,
            daysInMonth = 30,
        )
        assertTrue(insights.any { it.kind == LocalInsightKind.BUDGET && it.title.contains("偏快") })
    }

    @Test
    fun `does not restate mom or daily average`() {
        val insights = LocalInsightBuilder.build(
            periodType = ReportPeriodType.MONTHLY,
            totals = WindowTotals(0L, 20_000L),
            topCategories = emptyList(),
            uncategorizedCount = 0,
        )
        assertTrue(insights.none { it.kind == LocalInsightKind.TREND })
        assertTrue(insights.none { it.title.contains("日均") })
        assertTrue(insights.none { it.title.contains("环比") })
    }
}
