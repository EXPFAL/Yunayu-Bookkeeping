package com.expfal.yunayu.domain.report

import com.expfal.yunayu.domain.model.MonthlyBudgetSnapshot
import com.expfal.yunayu.domain.report.model.ReportPeriodType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ReportAllowanceCopyTest {

    @Test
    fun `daily spendable divides remaining by days`() {
        val snap = snapshot(remainingCents = 3_000L, remainingDays = 10)
        assertEquals(300L, ReportAllowanceCopy.dailySpendableCents(snap))
    }

    @Test
    fun `status line guides when no budget`() {
        assertTrue(
            ReportAllowanceCopy.statusLine(hasBudget = false, snapshot = null)
                .contains("设置每月预算"),
        )
    }

    @Test
    fun `status line flags pace when week over quota`() {
        val snap = snapshot(
            weeklyQuotaCents = 1_000L,
            spentThisWeekCents = 2_000L,
            remainingCents = 5_000L,
            remainingDays = 10,
        )
        assertTrue(ReportAllowanceCopy.statusLine(true, snap).contains("偏快"))
    }

    @Test
    fun `story prefix for weekly`() {
        val snap = snapshot(weeklyRemainingCents = 12_000L)
        val prefix = ReportAllowanceCopy.storyPrefix(ReportPeriodType.WEEKLY, true, snap)
        assertTrue(prefix!!.contains("本周"))
        assertNull(
            ReportAllowanceCopy.storyPrefix(ReportPeriodType.WEEKLY, false, snap),
        )
    }

    private fun snapshot(
        monthlyBudgetCents: Long = 100_000L,
        spentCents: Long = 20_000L,
        remainingCents: Long = 80_000L,
        remainingDays: Int = 20,
        weeklyQuotaCents: Long = 20_000L,
        spentThisWeekCents: Long = 5_000L,
        weeklyRemainingCents: Long = 15_000L,
    ) = MonthlyBudgetSnapshot(
        monthlyBudgetCents = monthlyBudgetCents,
        spentCents = spentCents,
        remainingCents = remainingCents,
        remainingDays = remainingDays,
        weeklyQuotaCents = weeklyQuotaCents,
        spentThisWeekCents = spentThisWeekCents,
        weeklyRemainingCents = weeklyRemainingCents,
    )
}
