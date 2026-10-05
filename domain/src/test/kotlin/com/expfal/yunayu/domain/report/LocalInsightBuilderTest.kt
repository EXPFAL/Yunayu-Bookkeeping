package com.expfal.yunayu.domain.report

import com.expfal.yunayu.domain.model.RecentTransaction
import com.expfal.yunayu.domain.model.TransactionType
import com.expfal.yunayu.domain.model.WindowTotals
import com.expfal.yunayu.domain.report.model.CategoryShare
import com.expfal.yunayu.domain.report.model.LocalInsightKind
import com.expfal.yunayu.domain.report.model.ReportPeriodType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.ZoneId

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
        assertTrue(insights.none { it.detail.contains("约合每天") })
        assertTrue(insights.none { it.title.contains("环比") })
    }

    @Test
    fun `stockpile pattern softens anomaly and rewrites budget ahead`() {
        val txs = listOf(
            expense(20_000L, "买罐头泡面", "餐饮", LocalDate.of(2026, 3, 5)),
            expense(5_000L, "方便面囤货", "餐饮", LocalDate.of(2026, 3, 6)),
        )
        val insights = LocalInsightBuilder.build(
            periodType = ReportPeriodType.MONTHLY,
            totals = WindowTotals(0L, 25_000L),
            topCategories = listOf(CategoryShare("餐饮", 25_000L, 100, tagId = 1L)),
            uncategorizedCount = 0,
            budgetCents = 50_000L,
            spentInBudgetMonthCents = 25_000L,
            elapsedDaysInMonth = 6,
            daysInMonth = 31,
            largeTxnCents = 20_000L,
            windowTransactions = txs,
        )
        assertTrue(insights.any { it.kind == LocalInsightKind.PATTERN && it.title.contains("囤货") })
        assertTrue(insights.none { it.kind == LocalInsightKind.ANOMALY })
        val budget = insights.single { it.kind == LocalInsightKind.BUDGET }
        assertTrue(budget.title.contains("偏快"))
        assertTrue(budget.detail.contains("囤货"))
        assertTrue(insights.any { it.kind == LocalInsightKind.STRUCTURE && it.title.contains("囤货") })
    }

    @Test
    fun `dining out pattern yields separate insight`() {
        val insights = LocalInsightBuilder.build(
            periodType = ReportPeriodType.MONTHLY,
            totals = WindowTotals(0L, 8_000L),
            topCategories = emptyList(),
            uncategorizedCount = 0,
            windowTransactions = listOf(
                expense(3_000L, "食堂午餐", "餐饮", LocalDate.of(2026, 3, 10)),
                expense(5_000L, null, "聚餐", LocalDate.of(2026, 3, 12)),
            ),
        )
        assertTrue(insights.any { it.kind == LocalInsightKind.PATTERN && it.title.contains("外出就餐") })
    }

    @Test
    fun `business pattern and net driven by business expense`() {
        val insights = LocalInsightBuilder.build(
            periodType = ReportPeriodType.MONTHLY,
            totals = WindowTotals(incomeCents = 5_000L, expenseCents = 20_000L),
            topCategories = emptyList(),
            uncategorizedCount = 0,
            windowTransactions = listOf(
                income(5_000L, "修车费", "兼职经营", LocalDate.of(2026, 3, 8)),
                expense(18_000L, "内胎配件", null, LocalDate.of(2026, 3, 9)),
                expense(2_000L, "食堂", "餐饮", LocalDate.of(2026, 3, 10)),
            ),
        )
        val business = insights.single { it.kind == LocalInsightKind.PATTERN && it.title.contains("兼职经营") }
        assertTrue(business.detail.contains("5"))
        assertTrue(business.detail.contains("180"))
        assertTrue(business.detail.contains("个人支出"))
        val summary = insights.single { it.kind == LocalInsightKind.SUMMARY }
        assertTrue(summary.detail.contains("经营"))
    }

    @Test
    fun `tagged stockpile skips anomaly and recognition wording`() {
        val insights = LocalInsightBuilder.build(
            periodType = ReportPeriodType.MONTHLY,
            totals = WindowTotals(0L, 20_000L),
            topCategories = listOf(CategoryShare("囤货三餐", 20_000L, 100, tagId = 1L)),
            uncategorizedCount = 0,
            largeTxnCents = 20_000L,
            windowTransactions = listOf(
                expense(20_000L, null, "囤货三餐", LocalDate.of(2026, 3, 5)),
            ),
        )
        val pattern = insights.single { it.kind == LocalInsightKind.PATTERN }
        assertTrue(pattern.title.contains("囤货"))
        assertTrue(pattern.detail.contains("笔囤货三餐"))
        assertTrue(insights.none { it.detail.contains("识别到") })
        assertTrue(insights.none { it.kind == LocalInsightKind.ANOMALY })
        assertTrue(insights.none { it.kind == LocalInsightKind.STRUCTURE })
        assertTrue(insights.none { it.detail.contains("约合每天") })
    }

    @Test
    fun `shopping parts note does not create business insight`() {
        val insights = LocalInsightBuilder.build(
            periodType = ReportPeriodType.MONTHLY,
            totals = WindowTotals(0L, 8_000L),
            topCategories = emptyList(),
            uncategorizedCount = 0,
            windowTransactions = listOf(
                expense(8_000L, "自行车配件", "购物", LocalDate.of(2026, 3, 9)),
            ),
        )
        assertTrue(insights.none { it.title.contains("兼职经营") })
    }

    @Test
    fun `single snack dining does not yield dining pattern`() {
        val insights = LocalInsightBuilder.build(
            periodType = ReportPeriodType.MONTHLY,
            totals = WindowTotals(0L, 8_000L),
            topCategories = emptyList(),
            uncategorizedCount = 0,
            windowTransactions = listOf(
                expense(990L, "蛋挞", "外出就餐", LocalDate.of(2026, 3, 10)),
            ),
        )
        assertTrue(insights.none { it.title.contains("外出就餐") })
        assertTrue(insights.none { it.title.contains("三餐结构") })
    }

    @Test
    fun `stockpile and dining mix yields one food structure card`() {
        val insights = LocalInsightBuilder.build(
            periodType = ReportPeriodType.MONTHLY,
            totals = WindowTotals(0L, 40_000L),
            topCategories = listOf(CategoryShare("囤货三餐", 20_000L, 50, tagId = 1L)),
            uncategorizedCount = 0,
            windowTransactions = listOf(
                expense(20_000L, null, "囤货三餐", LocalDate.of(2026, 3, 5)),
                expense(8_000L, "食堂午餐", "外出就餐", LocalDate.of(2026, 3, 10)),
                expense(5_000L, null, "聚餐", LocalDate.of(2026, 3, 12)),
                expense(7_000L, "华莱士", "外出就餐", LocalDate.of(2026, 3, 13)),
            ),
        )
        val mix = insights.single { it.kind == LocalInsightKind.STRUCTURE }
        assertTrue(mix.title.contains("三餐结构"))
        assertTrue(mix.detail.contains("囤货三餐"))
        assertTrue(mix.detail.contains("外出就餐"))
        assertTrue(mix.detail.contains("聚餐"))
        assertTrue(insights.none { it.kind == LocalInsightKind.PATTERN && it.title.contains("囤货") })
        assertTrue(insights.none { it.kind == LocalInsightKind.PATTERN && it.title.contains("外出就餐") })
    }

    @Test
    fun `budget ahead with business stock notes personal pace`() {
        val insights = LocalInsightBuilder.build(
            periodType = ReportPeriodType.MONTHLY,
            totals = WindowTotals(0L, 40_000L),
            topCategories = emptyList(),
            uncategorizedCount = 0,
            budgetCents = 50_000L,
            spentInBudgetMonthCents = 40_000L,
            elapsedDaysInMonth = 5,
            daysInMonth = 30,
            windowTransactions = listOf(
                expense(20_000L, null, "经营进货", LocalDate.of(2026, 3, 4)),
                expense(20_000L, "食堂", "外出就餐", LocalDate.of(2026, 3, 5)),
            ),
        )
        val budget = insights.single { it.kind == LocalInsightKind.BUDGET }
        assertTrue(budget.title.contains("偏快"))
        assertTrue(budget.detail.contains("店用进货"))
        assertTrue(budget.detail.contains("个人节奏"))
    }

    private fun expense(
        amountCents: Long,
        note: String?,
        tagName: String?,
        date: LocalDate,
    ): RecentTransaction = RecentTransaction(
        id = amountCents,
        amountCents = amountCents,
        type = TransactionType.EXPENSE,
        tagName = tagName,
        occurredAt = startOfDayMillis(date),
        note = note,
    )

    private fun income(
        amountCents: Long,
        note: String?,
        tagName: String?,
        date: LocalDate,
    ): RecentTransaction = RecentTransaction(
        id = amountCents + 1,
        amountCents = amountCents,
        type = TransactionType.INCOME,
        tagName = tagName,
        occurredAt = startOfDayMillis(date),
        note = note,
    )

    private fun startOfDayMillis(date: LocalDate): Long =
        date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
}
