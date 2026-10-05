package com.expfal.yunayu.domain.report

import com.expfal.yunayu.domain.model.ExpenseSeedTags
import com.expfal.yunayu.domain.model.RecentTransaction
import com.expfal.yunayu.domain.model.TransactionType
import com.expfal.yunayu.domain.model.WindowTotals
import com.expfal.yunayu.domain.report.LifestyleHeuristics.LifestyleKind
import com.expfal.yunayu.domain.report.model.CategoryShare
import com.expfal.yunayu.domain.report.model.LocalInsight
import com.expfal.yunayu.domain.report.model.LocalInsightKind
import com.expfal.yunayu.domain.report.model.ReportPeriodType
import java.util.Locale
import kotlin.math.abs

/**
 * 本地规则洞察构建器（纯函数、无 IO）。
 *
 * 只保留概览里没有的信息：空窗、净结余、分类结构、预算节奏、未分类、大额、
 * 以及囤货三餐 / 外出就餐 / 兼职经营等生活方式模式。环比与日均已在概览中展示，不再复述。
 */
object LocalInsightBuilder {

    /**
     * @param periodType 周期类型（影响预算规则措辞）
     * @param totals 当期收支
     * @param topCategories 当期 Top 分类
     * @param uncategorizedCount 窗口内未分类笔数
     * @param budgetCents 月预算（分）；≤0 表示未设置
     * @param spentInBudgetMonthCents 当月已支出（分），用于月报/周报预算进度
     * @param elapsedDaysInMonth 本月已过天数（含今天）
     * @param daysInMonth 本月总天数
     * @param largeTxnCents 窗口内最大单笔支出（分）；null 表示未知，跳过异常规则
     * @param windowTransactions 当期流水（含备注与标签），用于生活方式启发式；可空
     */
    fun build(
        periodType: ReportPeriodType,
        totals: WindowTotals,
        topCategories: List<CategoryShare>,
        uncategorizedCount: Int,
        budgetCents: Long = 0L,
        spentInBudgetMonthCents: Long = 0L,
        elapsedDaysInMonth: Int = 1,
        daysInMonth: Int = 30,
        largeTxnCents: Long? = null,
        windowTransactions: List<RecentTransaction> = emptyList(),
    ): List<LocalInsight> {
        if (totals.incomeCents == 0L && totals.expenseCents == 0L) {
            return listOf(
                LocalInsight(
                    kind = LocalInsightKind.SUMMARY,
                    title = "本期暂无收支",
                    detail = "该时间窗内还没有记账记录，记几笔后再来看复盘会更有参考价值。",
                ),
            )
        }

        val lifestyle = LifestyleSnapshot.of(windowTransactions, totals.expenseCents)
        val insights = mutableListOf<LocalInsight>()
        addNetInsight(insights, totals, lifestyle)
        addTopCategoryInsight(insights, totals, topCategories, lifestyle)
        addFoodInsights(insights, lifestyle)
        addBusinessInsight(insights, lifestyle)
        if (budgetCents > 0L) {
            addBudgetInsight(
                insights = insights,
                periodType = periodType,
                budgetCents = budgetCents,
                spentCents = spentInBudgetMonthCents,
                elapsedDays = elapsedDaysInMonth.coerceAtLeast(1),
                daysInMonth = daysInMonth.coerceAtLeast(1),
                stockpileCents = if (lifestyle.hasStockpilePattern) lifestyle.stockpileCents else 0L,
                businessExpenseCents = lifestyle.businessExpenseCents,
            )
        }
        if (uncategorizedCount > 0) {
            insights += LocalInsight(
                kind = LocalInsightKind.DATA_QUALITY,
                title = "有 $uncategorizedCount 笔未分类",
                detail = "未分类记录会影响占比准确性，可在「整理」里批量补标签。",
            )
        }
        addAnomalyInsight(insights, largeTxnCents, budgetCents, lifestyle.hasStockpilePattern)
        return insights
    }

    private fun addNetInsight(
        insights: MutableList<LocalInsight>,
        totals: WindowTotals,
        lifestyle: LifestyleSnapshot,
    ) {
        val net = totals.incomeCents - totals.expenseCents
        if (net >= 0L) return
        val businessHeavy = lifestyle.businessExpenseCents > 0L &&
            lifestyle.businessExpenseCents * 100 >= abs(net) * BUSINESS_DRIVES_NET_PERCENT
        insights += LocalInsight(
            kind = LocalInsightKind.SUMMARY,
            title = "本期净结余为负",
            detail = if (businessHeavy) {
                "支出比收入多 ${formatYuan(abs(net))} 元，其中经营进货/工具约 ${formatYuan(lifestyle.businessExpenseCents)} 元，" +
                    "不一定是生活入不敷出。"
            } else {
                "支出比收入多 ${formatYuan(abs(net))} 元，可以留意大额或高频分类。"
            },
        )
    }

    private fun addTopCategoryInsight(
        insights: MutableList<LocalInsight>,
        totals: WindowTotals,
        topCategories: List<CategoryShare>,
        lifestyle: LifestyleSnapshot,
    ) {
        val top = topCategories.firstOrNull() ?: return
        if (totals.expenseCents <= 0L || top.percent < TOP_CATEGORY_HEAVY_PERCENT) return
        val name = top.tagName ?: "未分类"
        if (name == ExpenseSeedTags.TAG_STOCKPILE ||
            name == ExpenseSeedTags.TAG_DINING_OUT ||
            lifestyle.hasFoodMix
        ) {
            return
        }
        val isLegacyFood = name.contains("餐饮") || name == ExpenseSeedTags.TAG_GATHERING
        val stockpileDominatesLegacy =
            isLegacyFood && lifestyle.hasStockpilePattern && lifestyle.stockpileCents * 2 >= top.cents
        insights += if (stockpileDominatesLegacy) {
            LocalInsight(
                kind = LocalInsightKind.STRUCTURE,
                title = "「$name」里主要是囤货",
                detail = "该分类约占本期支出的 ${top.percent}%，其中囤货三餐约 ${formatYuan(lifestyle.stockpileCents)} 元，" +
                    "更像集中采购日常餐食，而非频繁下馆子。",
            )
        } else {
            LocalInsight(
                kind = LocalInsightKind.STRUCTURE,
                title = "「$name」占比偏高",
                detail = "该分类约占本期支出的 ${top.percent}%，可考虑是否拆分或设预算上限。",
            )
        }
    }

    private fun addFoodInsights(insights: MutableList<LocalInsight>, lifestyle: LifestyleSnapshot) {
        if (lifestyle.hasFoodMix) {
            insights += LocalInsight(
                kind = LocalInsightKind.STRUCTURE,
                title = "三餐结构",
                detail = foodMixDetail(lifestyle),
            )
            return
        }
        if (lifestyle.hasStockpilePattern) {
            insights += LocalInsight(
                kind = LocalInsightKind.PATTERN,
                title = "囤货三餐",
                detail = if (lifestyle.taggedStockpile) {
                    "本期 ${lifestyle.stockpileCount} 笔囤货三餐共 ${formatYuan(lifestyle.stockpileCents)} 元，是集中采购日常餐食。"
                } else {
                    "本期识别到 ${lifestyle.stockpileCount} 笔囤货支出共 ${formatYuan(lifestyle.stockpileCents)} 元，通常是集中采购速食作为日常三餐。"
                },
            )
        }
        if (lifestyle.hasDiningOut) {
            insights += LocalInsight(
                kind = LocalInsightKind.PATTERN,
                title = "外出就餐",
                detail = if (lifestyle.taggedDining) {
                    "本期 ${lifestyle.diningOutCount} 笔食堂/聚餐等外出就餐，合计 ${formatYuan(lifestyle.diningOutCents)} 元。"
                } else {
                    "本期识别到 ${lifestyle.diningOutCount} 笔食堂/聚餐等外出就餐，合计 ${formatYuan(lifestyle.diningOutCents)} 元。"
                },
            )
        }
    }

    private fun addBusinessInsight(insights: MutableList<LocalInsight>, lifestyle: LifestyleSnapshot) {
        if (!lifestyle.hasBusiness) return
        val margin = lifestyle.businessIncomeCents - lifestyle.businessExpenseCents
        insights += LocalInsight(
            kind = LocalInsightKind.PATTERN,
            title = "兼职经营",
            detail = "经营入账 ${formatYuan(lifestyle.businessIncomeCents)} 元，进货/工具 ${formatYuan(lifestyle.businessExpenseCents)} 元，" +
                "差额 ${formatYuan(margin)} 元（未摊库存，仅作参考）。" +
                " 个人支出 ${formatYuan(lifestyle.personalExpenseCents)} 元。",
        )
    }

    private fun addAnomalyInsight(
        insights: MutableList<LocalInsight>,
        largeTxnCents: Long?,
        budgetCents: Long,
        hasStockpilePattern: Boolean,
    ) {
        if (largeTxnCents == null || largeTxnCents <= 0L || hasStockpilePattern) return
        val threshold = if (budgetCents > 0L) {
            budgetCents * LARGE_TXN_BUDGET_RATIO_PERCENT / 100L
        } else {
            DEFAULT_LARGE_TXN_CENTS
        }
        if (largeTxnCents < threshold) return
        insights += LocalInsight(
            kind = LocalInsightKind.ANOMALY,
            title = "存在较大单笔支出",
            detail = "最大单笔约 ${formatYuan(largeTxnCents)} 元，建议确认是否为必要开支。",
        )
    }

    private fun addBudgetInsight(
        insights: MutableList<LocalInsight>,
        periodType: ReportPeriodType,
        budgetCents: Long,
        spentCents: Long,
        elapsedDays: Int,
        daysInMonth: Int,
        stockpileCents: Long,
        businessExpenseCents: Long,
    ) {
        val usedRatio = spentCents * 100.0 / budgetCents
        val timeRatio = elapsedDays * 100.0 / daysInMonth
        val gap = usedRatio - timeRatio
        val businessNote = if (
            gap >= BUDGET_AHEAD_GAP_PERCENT &&
            spentCents > 0L &&
            businessExpenseCents * 100 >= spentCents * BUSINESS_IN_SPENT_PERCENT
        ) {
            " 已用金额含店用进货约 ${formatYuan(businessExpenseCents)} 元，个人节奏另看。"
        } else {
            ""
        }
        val paceDetail = budgetDetail(periodType, usedRatio, timeRatio, spentCents, budgetCents)
        when {
            gap >= BUDGET_AHEAD_GAP_PERCENT -> {
                val stockpileNote = if (stockpileCents > 0L) {
                    " 其中囤货三餐约 ${formatYuan(stockpileCents)} 元，像是集中采购而非日常冲动消费。"
                } else {
                    ""
                }
                insights += LocalInsight(
                    kind = LocalInsightKind.BUDGET,
                    title = "预算消耗偏快",
                    detail = paceDetail + stockpileNote + businessNote,
                )
            }
            gap <= -BUDGET_AHEAD_GAP_PERCENT -> insights += LocalInsight(
                kind = LocalInsightKind.BUDGET,
                title = "预算进度偏慢",
                detail = paceDetail,
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
        val prefix = if (periodType == ReportPeriodType.WEEKLY) "结合本月累计：" else ""
        return "${prefix}已花 ${formatYuan(spentCents)} / ${formatYuan(budgetCents)} 元" +
            "（${formatPercent(usedRatio)}），日历进度约 ${formatPercent(timeRatio)}。"
    }

    private fun foodMixDetail(lifestyle: LifestyleSnapshot): String {
        val foodCents = lifestyle.stockpileCents + lifestyle.diningOutCents
        val shareOfPersonal = if (lifestyle.personalExpenseCents > 0L) {
            (foodCents * 100 / lifestyle.personalExpenseCents).toInt()
        } else {
            0
        }
        fun part(label: String, cents: Long): String? {
            if (cents <= 0L || foodCents <= 0L) return null
            val pct = (cents * 100 / foodCents).toInt()
            return "$label ${formatYuan(cents)} 元（$pct%）"
        }
        val parts = listOfNotNull(
            part("囤货三餐", lifestyle.stockpileCents),
            part("外出就餐", lifestyle.diningOutOnlyCents),
            part("聚餐", lifestyle.gatheringCents),
        )
        val breakdown = if (parts.isEmpty()) "" else " ${parts.joinToString("，")}。"
        return "个人餐饮合计 ${formatYuan(foodCents)} 元，约占个人支出的 $shareOfPersonal%。$breakdown"
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
}

private data class LifestyleSnapshot(
    val stockpileCents: Long,
    val stockpileCount: Int,
    val diningOutCents: Long,
    val diningOutCount: Int,
    val diningOutOnlyCents: Long,
    val gatheringCents: Long,
    val businessExpenseCents: Long,
    val businessIncomeCents: Long,
    val personalExpenseCents: Long,
    val hasStockpilePattern: Boolean,
    val hasDiningOut: Boolean,
    val hasFoodMix: Boolean,
    val hasBusiness: Boolean,
    val taggedStockpile: Boolean,
    val taggedDining: Boolean,
) {
    companion object {
        fun of(transactions: List<RecentTransaction>, expenseCents: Long): LifestyleSnapshot {
            val classified = transactions.map { tx ->
                tx to LifestyleHeuristics.classify(tx.type, tx.note, tx.tagName)
            }
            val stockpileCents = sumExpense(classified, LifestyleKind.STOCKPILE)
            val stockpileCount = countExpense(classified, LifestyleKind.STOCKPILE)
            val diningOutCents = sumExpense(classified, LifestyleKind.DINING_OUT)
            val diningOutCount = countExpense(classified, LifestyleKind.DINING_OUT)
            val gatheringCents = classified
                .filter {
                    it.first.type == TransactionType.EXPENSE &&
                        it.first.tagName == ExpenseSeedTags.TAG_GATHERING
                }
                .sumOf { it.first.amountCents }
            val businessExpenseCents = sumExpense(classified, LifestyleKind.BUSINESS_EXPENSE)
            val businessIncomeCents = classified
                .filter { it.second == LifestyleKind.BUSINESS_INCOME }
                .sumOf { it.first.amountCents }
            val personalExpenseCents = (expenseCents - businessExpenseCents).coerceAtLeast(0L)
            val hasStockpilePattern = stockpileCents >= STOCKPILE_AMOUNT_THRESHOLD_CENTS ||
                stockpileCount >= STOCKPILE_COUNT_THRESHOLD
            val hasDiningOut = diningOutCount >= DINING_COUNT_THRESHOLD ||
                (personalExpenseCents > 0L &&
                    diningOutCents * 100 >= personalExpenseCents * DINING_PERSONAL_PERCENT)
            return LifestyleSnapshot(
                stockpileCents = stockpileCents,
                stockpileCount = stockpileCount,
                diningOutCents = diningOutCents,
                diningOutCount = diningOutCount,
                diningOutOnlyCents = (diningOutCents - gatheringCents).coerceAtLeast(0L),
                gatheringCents = gatheringCents,
                businessExpenseCents = businessExpenseCents,
                businessIncomeCents = businessIncomeCents,
                personalExpenseCents = personalExpenseCents,
                hasStockpilePattern = hasStockpilePattern,
                hasDiningOut = hasDiningOut,
                hasFoodMix = hasStockpilePattern && hasDiningOut,
                hasBusiness = businessExpenseCents > 0L || businessIncomeCents > 0L,
                taggedStockpile = classified.any { (tx, kind) ->
                    kind == LifestyleKind.STOCKPILE &&
                        (tx.tagName == ExpenseSeedTags.TAG_STOCKPILE ||
                            tx.tagName == ExpenseSeedTags.TAG_FRUIT_LEGACY)
                },
                taggedDining = classified.any { (tx, kind) ->
                    kind == LifestyleKind.DINING_OUT &&
                        (tx.tagName == ExpenseSeedTags.TAG_DINING_OUT ||
                            tx.tagName == ExpenseSeedTags.TAG_GATHERING)
                },
            )
        }

        private fun sumExpense(
            classified: List<Pair<RecentTransaction, LifestyleKind>>,
            kind: LifestyleKind,
        ): Long = classified
            .filter { it.first.type == TransactionType.EXPENSE && it.second == kind }
            .sumOf { it.first.amountCents }

        private fun countExpense(
            classified: List<Pair<RecentTransaction, LifestyleKind>>,
            kind: LifestyleKind,
        ): Int = classified.count { it.first.type == TransactionType.EXPENSE && it.second == kind }
    }
}

private const val TOP_CATEGORY_HEAVY_PERCENT = 40
private const val BUDGET_AHEAD_GAP_PERCENT = 15.0
private const val LARGE_TXN_BUDGET_RATIO_PERCENT = 20L
private const val DEFAULT_LARGE_TXN_CENTS = 20_000L
private const val STOCKPILE_AMOUNT_THRESHOLD_CENTS = 15_000L
private const val STOCKPILE_COUNT_THRESHOLD = 2
private const val DINING_COUNT_THRESHOLD = 3
private const val DINING_PERSONAL_PERCENT = 15L
private const val BUSINESS_DRIVES_NET_PERCENT = 50L
private const val BUSINESS_IN_SPENT_PERCENT = 20L

