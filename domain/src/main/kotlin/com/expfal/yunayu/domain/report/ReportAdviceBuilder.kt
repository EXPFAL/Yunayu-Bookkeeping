package com.expfal.yunayu.domain.report

import com.expfal.yunayu.domain.model.WindowTotals
import com.expfal.yunayu.domain.report.model.CategoryChange
import com.expfal.yunayu.domain.report.model.CategoryShare
import com.expfal.yunayu.domain.report.model.LocalInsight
import com.expfal.yunayu.domain.report.model.LocalInsightKind
/**
 * 规则建议器：按触发条件产出 0–2 条 [LocalInsightKind.ADVICE]。
 */
object ReportAdviceBuilder {

    private const val MAX_ADVICE = 2
    private const val BUDGET_PACE_RATIO = 1.15
    private const val STRUCTURE_SHARE_PCT = 45
    private const val CATEGORY_SURGE_CENTS = 5_000L
    private const val UNCATEGORIZED_THRESHOLD = 3

    fun build(
        totals: WindowTotals,
        topCategories: List<CategoryShare>,
        topIncreases: List<CategoryChange>,
        uncategorizedCount: Int,
        budgetCents: Long,
        spentInBudgetMonthCents: Long,
        elapsedDaysInMonth: Int,
        daysInMonth: Int,
    ): List<LocalInsight> {
        val advice = mutableListOf<LocalInsight>()

        if (budgetCents > 0L) {
            val elapsed = elapsedDaysInMonth.coerceAtLeast(1)
            val days = daysInMonth.coerceAtLeast(1)
            val calendarProgress = elapsed.toDouble() / days
            val spendProgress = spentInBudgetMonthCents.toDouble() / budgetCents
            if (spendProgress > calendarProgress * BUDGET_PACE_RATIO) {
                advice += LocalInsight(
                    kind = LocalInsightKind.ADVICE,
                    title = "放缓非必要支出节奏",
                    detail = "本月预算消耗快于日历进度，可先压低可选消费，或在下月微调预算额度。",
                )
            }
        }

        val surge = topIncreases.firstOrNull { it.deltaCents >= CATEGORY_SURGE_CENTS }
        val heavy = topCategories.firstOrNull { it.percent >= STRUCTURE_SHARE_PCT }
        when {
            surge != null && advice.size < MAX_ADVICE -> {
                val name = surge.tagName ?: "未分类"
                advice += LocalInsight(
                    kind = LocalInsightKind.ADVICE,
                    title = "给「$name」设上限",
                    detail = "该分类环比明显上升，可在标签管理里单独跟踪，或拆分更细的标签方便控量。",
                )
            }
            heavy != null && advice.size < MAX_ADVICE -> {
                val name = heavy.tagName ?: "未分类"
                advice += LocalInsight(
                    kind = LocalInsightKind.ADVICE,
                    title = "拆分或压低「$name」占比",
                    detail = "该分类已占支出 ${heavy.percent}%，结构偏集中，建议拆标签或设本期上限。",
                )
            }
        }

        val net = totals.incomeCents - totals.expenseCents
        if (net < 0L && advice.size < MAX_ADVICE) {
            advice += LocalInsight(
                kind = LocalInsightKind.ADVICE,
                title = "控制大额与可选支出",
                detail = "本期净结余为负，可先盯住大额单笔与环比上升的分类，避免连续透支。",
            )
        }

        if (uncategorizedCount >= UNCATEGORIZED_THRESHOLD && advice.size < MAX_ADVICE) {
            advice += LocalInsight(
                kind = LocalInsightKind.ADVICE,
                title = "去「整理」补标签",
                detail = "未分类已有 $uncategorizedCount 笔，补齐标签后占比与建议会更准。",
            )
        }

        return advice.take(MAX_ADVICE)
    }
}
