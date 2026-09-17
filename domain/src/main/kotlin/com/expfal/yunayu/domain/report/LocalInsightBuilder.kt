package com.expfal.yunayu.domain.report

import com.expfal.yunayu.domain.model.WindowTotals
import com.expfal.yunayu.domain.report.model.CategoryShare
import com.expfal.yunayu.domain.report.model.LocalInsight
import com.expfal.yunayu.domain.report.model.LocalInsightKind
import com.expfal.yunayu.domain.report.model.ReportPeriodType
import java.util.Locale
import kotlin.math.abs

/**
 * 本地规则洞察构建器（纯函数、无 IO）。
 *
 * 入参为已聚合的窗口数据；预算相关字段可选（未设预算则跳过预算规则）。
 */
object LocalInsightBuilder {

    /**
     * @param periodType 周期类型（影响预算规则措辞）
     * @param totals 当期收支
     * @param prevTotals 上期收支
     * @param topCategories 当期 Top 分类
     * @param uncategorizedCount 窗口内未分类笔数
     * @param budgetCents 月预算（分）；≤0 表示未设置
     * @param spentInBudgetMonthCents 当月已支出（分），用于月报/周报预算进度；周报可传本月累计
     * @param elapsedDaysInMonth 本月已过天数（含今天）
     * @param daysInMonth 本月总天数
     * @param largeTxnCents 窗口内最大单笔支出（分）；null 表示未知，跳过异常规则
     * @param windowDayCount 报告窗口自然日数（半开区间折算）
     */
    fun build(
        periodType: ReportPeriodType,
        totals: WindowTotals,
        prevTotals: WindowTotals,
        topCategories: List<CategoryShare>,
        uncategorizedCount: Int,
        budgetCents: Long = 0L,
        spentInBudgetMonthCents: Long = 0L,
        elapsedDaysInMonth: Int = 1,
        daysInMonth: Int = 30,
        largeTxnCents: Long? = null,
        windowDayCount: Int = 1,
    ): List<LocalInsight> {
        val insights = mutableListOf<LocalInsight>()

        if (totals.incomeCents == 0L && totals.expenseCents == 0L) {
            insights += LocalInsight(
                kind = LocalInsightKind.SUMMARY,
                title = "本期暂无收支",
                detail = "该时间窗内还没有记账记录，记几笔后再来看复盘会更有参考价值。",
            )
            return insights
        }

        val net = totals.incomeCents - totals.expenseCents
        if (net < 0L) {
            insights += LocalInsight(
                kind = LocalInsightKind.SUMMARY,
                title = "本期净结余为负",
                detail = "支出比收入多 ${formatYuan(abs(net))} 元，可以留意大额或高频分类。",
            )
        }

        addMoMInsight(
            insights = insights,
            label = "支出",
            current = totals.expenseCents,
            previous = prevTotals.expenseCents,
            riseKind = LocalInsightKind.TREND,
        )
        addMoMInsight(
            insights = insights,
            label = "收入",
            current = totals.incomeCents,
            previous = prevTotals.incomeCents,
            riseKind = LocalInsightKind.TREND,
        )

        val top = topCategories.firstOrNull()
        if (top != null && totals.expenseCents > 0L && top.percent >= TOP_CATEGORY_HEAVY_PERCENT) {
            val name = top.tagName ?: "未分类"
            insights += LocalInsight(
                kind = LocalInsightKind.STRUCTURE,
                title = "「$name」占比偏高",
                detail = "该分类约占本期支出的 ${top.percent}%，可考虑是否拆分或设预算上限。",
            )
        }

        if (budgetCents > 0L) {
            addBudgetInsight(
                insights = insights,
                periodType = periodType,
                budgetCents = budgetCents,
                spentCents = spentInBudgetMonthCents,
                elapsedDays = elapsedDaysInMonth.coerceAtLeast(1),
                daysInMonth = daysInMonth.coerceAtLeast(1),
            )
        }

        if (uncategorizedCount > 0) {
            insights += LocalInsight(
                kind = LocalInsightKind.DATA_QUALITY,
                title = "有 $uncategorizedCount 笔未分类",
                detail = "未分类记录会影响占比准确性，可在「整理」里批量补标签。",
            )
        }

        if (largeTxnCents != null && largeTxnCents > 0L) {
            val threshold = when {
                budgetCents > 0L -> (budgetCents * LARGE_TXN_BUDGET_RATIO_PERCENT / 100L)
                else -> DEFAULT_LARGE_TXN_CENTS
            }
            if (largeTxnCents >= threshold) {
                insights += LocalInsight(
                    kind = LocalInsightKind.ANOMALY,
                    title = "存在较大单笔支出",
                    detail = "最大单笔约 ${formatYuan(largeTxnCents)} 元，建议确认是否为必要开支。",
                )
            }
        }

        val days = windowDayCount.coerceAtLeast(1)
        if (totals.expenseCents > 0L && days >= 3) {
            val daily = totals.expenseCents / days
            insights += LocalInsight(
                kind = LocalInsightKind.SUMMARY,
                title = "日均支出约 ${formatYuan(daily)} 元",
                detail = "按本期 $days 天折算；可与周可用额度对照，避免前期花太猛。",
            )
        }

        return insights
    }

    private fun addMoMInsight(
        insights: MutableList<LocalInsight>,
        label: String,
        current: Long,
        previous: Long,
        riseKind: LocalInsightKind,
    ) {
        if (previous <= 0L) return
        val diff = current - previous
        val ratio = abs(diff) * 100.0 / previous
        if (ratio < MOM_THRESHOLD_PERCENT) return
        val direction = if (diff > 0) "上升" else "下降"
        insights += LocalInsight(
            kind = riseKind,
            title = "${label}环比$direction ${formatPercent(ratio)}",
            detail = "相对上期${label}${direction}约 ${formatPercent(ratio)}（${formatYuan(abs(diff))} 元）。",
        )
    }

    private fun addBudgetInsight(
        insights: MutableList<LocalInsight>,
        periodType: ReportPeriodType,
        budgetCents: Long,
        spentCents: Long,
        elapsedDays: Int,
        daysInMonth: Int,
    ) {
        val usedRatio = spentCents * 100.0 / budgetCents
        val timeRatio = elapsedDays * 100.0 / daysInMonth
        val gap = usedRatio - timeRatio
        when {
            gap >= BUDGET_AHEAD_GAP_PERCENT -> insights += LocalInsight(
                kind = LocalInsightKind.BUDGET,
                title = "预算消耗偏快",
                detail = budgetDetail(periodType, usedRatio, timeRatio, spentCents, budgetCents),
            )
            gap <= -BUDGET_AHEAD_GAP_PERCENT -> insights += LocalInsight(
                kind = LocalInsightKind.BUDGET,
                title = "预算进度偏慢",
                detail = budgetDetail(periodType, usedRatio, timeRatio, spentCents, budgetCents),
            )
            else -> insights += LocalInsight(
                kind = LocalInsightKind.BUDGET,
                title = "预算进度正常",
                detail = "本月已用 ${formatPercent(usedRatio)}，时间进度约 ${formatPercent(timeRatio)}。",
            )
        }
    }

    private fun budgetDetail(
        periodType: ReportPeriodType,
        usedRatio: Double,
        timeRatio: Double,
        spentCents: Long,
        budgetCents: Long,
    ): String {
        val prefix = when (periodType) {
            ReportPeriodType.WEEKLY -> "结合本月累计："
            else -> ""
        }
        return "${prefix}已花 ${formatYuan(spentCents)} / ${formatYuan(budgetCents)} 元" +
            "（${formatPercent(usedRatio)}），日历进度约 ${formatPercent(timeRatio)}。"
    }

    private fun formatYuan(cents: Long): String {
        val abs = if (cents < 0) -cents else cents
        val sign = if (cents < 0) "-" else ""
        return if (abs % 100L == 0L) {
            "$sign${abs / 100}"
        } else {
            String.format(Locale.US, "%s%.2f", sign, abs / 100.0)
        }
    }

    private fun formatPercent(ratio: Double): String =
        String.format(Locale.US, "%.0f%%", ratio)

    private const val MOM_THRESHOLD_PERCENT = 30.0
    private const val TOP_CATEGORY_HEAVY_PERCENT = 40
    private const val BUDGET_AHEAD_GAP_PERCENT = 15.0
    private const val LARGE_TXN_BUDGET_RATIO_PERCENT = 20L
    private const val DEFAULT_LARGE_TXN_CENTS = 20_000L
}
