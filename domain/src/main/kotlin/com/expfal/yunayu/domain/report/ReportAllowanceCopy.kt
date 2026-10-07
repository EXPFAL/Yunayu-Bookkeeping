package com.expfal.yunayu.domain.report

import com.expfal.yunayu.domain.model.MonthlyBudgetSnapshot
import com.expfal.yunayu.domain.report.model.ReportPeriodType
import java.util.Locale

/**
 * 报告生活费额度展示文案（纯函数，不改预算引擎）。
 */
object ReportAllowanceCopy {

    /** 日均可花（分）= 本月剩余 ÷ max(剩余天数, 1)。 */
    fun dailySpendableCents(snapshot: MonthlyBudgetSnapshot): Long {
        val days = snapshot.remainingDays.coerceAtLeast(1)
        return snapshot.remainingCents / days.toLong()
    }

    /**
     * Hero 状态句。无预算时返回引导句；本周支出超过周额度则偏快；否则节奏尚可。
     */
    fun statusLine(hasBudget: Boolean, snapshot: MonthlyBudgetSnapshot?): String {
        if (!hasBudget || snapshot == null) {
            return "设置每月预算，看清每周能花多少"
        }
        val daily = dailySpendableCents(snapshot)
        return if (snapshot.spentThisWeekCents > snapshot.weeklyQuotaCents &&
            snapshot.weeklyQuotaCents > 0L
        ) {
            "本周节奏偏快，建议日均控制在 ${formatYuan(daily)} 内"
        } else {
            "本周节奏尚可，日均可花约 ${formatYuan(daily)}"
        }
    }

    /** 详情故事段可选额度前缀（展示层拼接，不写回 STORY）。 */
    fun storyPrefix(
        periodType: ReportPeriodType,
        hasBudget: Boolean,
        snapshot: MonthlyBudgetSnapshot?,
    ): String? {
        if (!hasBudget || snapshot == null) return null
        return when (periodType) {
            ReportPeriodType.WEEKLY ->
                "本周还剩可花 ${formatYuan(snapshot.weeklyRemainingCents)}。"
            ReportPeriodType.MONTHLY ->
                "本月还剩可花 ${formatYuan(snapshot.remainingCents)}。"
            ReportPeriodType.ANNUAL -> null
        }
    }

    private fun formatYuan(cents: Long): String {
        val yuan = cents / 100.0
        return if (yuan == yuan.toLong().toDouble()) {
            String.format(Locale.CHINA, "¥%.0f", yuan)
        } else {
            String.format(Locale.CHINA, "¥%.2f", yuan)
        }
    }
}
