package com.expfal.yunayu.domain.report

import com.expfal.yunayu.domain.model.WindowTotals
import com.expfal.yunayu.domain.report.model.CategoryChange
import com.expfal.yunayu.domain.report.model.CategoryShare
import com.expfal.yunayu.domain.report.model.LocalInsight
import com.expfal.yunayu.domain.report.model.LocalInsightKind
import com.expfal.yunayu.domain.report.model.MomComparison
import com.expfal.yunayu.domain.report.model.PeriodTotalsPoint
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ReportNarrativeAndAdviceTest {

    @Test
    fun `narrative includes overview discovery and stays within length`() {
        val totals = WindowTotals(10_000L, 8_000L)
        val facts = listOf(
            ScoredFact(1.0, "支出环比升 20 元", LocalInsightKind.TREND),
            ScoredFact(0.9, "「餐饮」占支出 50%", LocalInsightKind.STRUCTURE),
        )
        val story = ReportNarrativeBuilder.build(totals, facts, listOf("放缓非必要支出节奏"))
        assertEquals(LocalInsightKind.STORY, story.kind)
        assertTrue(story.detail.contains("收入"))
        assertTrue(story.detail.contains("餐饮") || story.detail.contains("环比"))
        assertTrue(story.detail.length in 40..180)
    }

    @Test
    fun `empty window narrative`() {
        val story = ReportNarrativeBuilder.build(WindowTotals(0, 0), emptyList(), emptyList())
        assertTrue(story.detail.contains("暂无"))
    }

    @Test
    fun `advice triggers on budget pace and negative net`() {
        val advice = ReportAdviceBuilder.build(
            totals = WindowTotals(1_000L, 5_000L),
            topCategories = listOf(CategoryShare("餐饮", 4_000L, 80)),
            topIncreases = listOf(
                CategoryChange("餐饮", 4_000L, 500L),
            ),
            uncategorizedCount = 0,
            budgetCents = 10_000L,
            spentInBudgetMonthCents = 9_000L,
            elapsedDaysInMonth = 10,
            daysInMonth = 30,
        )
        assertTrue(advice.size in 1..2)
        assertTrue(advice.all { it.kind == LocalInsightKind.ADVICE })
        assertTrue(advice.any { it.title.contains("节奏") || it.title.contains("餐饮") || it.title.contains("大额") })
    }

    @Test
    fun `no advice when calm`() {
        val advice = ReportAdviceBuilder.build(
            totals = WindowTotals(10_000L, 3_000L),
            topCategories = listOf(CategoryShare("餐饮", 1_000L, 33)),
            topIncreases = emptyList(),
            uncategorizedCount = 0,
            budgetCents = 0L,
            spentInBudgetMonthCents = 0L,
            elapsedDaysInMonth = 15,
            daysInMonth = 30,
        )
        assertTrue(advice.isEmpty())
    }

    @Test
    fun `fact scorer ranks mom facts`() {
        val scored = ReportFactScorer.score(
            totals = WindowTotals(5_000L, 4_000L),
            mom = MomComparison(0, 2_000L, -2_000L, 100),
            points = listOf(
                PeriodTotalsPoint("a", 0, 1_000),
                PeriodTotalsPoint("b", 0, 4_000),
            ),
            topCategories = listOf(CategoryShare("餐饮", 3_000L, 75)),
            detailInsights = listOf(
                LocalInsight(LocalInsightKind.BUDGET, "预算偏快", "消耗快于日历"),
            ),
        )
        assertTrue(scored.isNotEmpty())
        assertTrue(scored.first().score >= scored.last().score)
    }
}
