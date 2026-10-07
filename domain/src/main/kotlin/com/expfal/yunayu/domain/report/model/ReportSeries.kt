package com.expfal.yunayu.domain.report.model

/**
 * 近 N 期中单期的收支快照（按时间升序，最旧在前）。
 */
data class PeriodTotalsPoint(
    val periodKey: String,
    val incomeCents: Long,
    val expenseCents: Long,
) {
    val netCents: Long get() = incomeCents - expenseCents
}

/** 本期相对上期的环比差额。 */
data class MomComparison(
    val incomeDeltaCents: Long,
    val expenseDeltaCents: Long,
    val netDeltaCents: Long,
    /** 支出环比百分点；上期支出为 0 时为 null。 */
    val expenseDeltaPercent: Int?,
)

/** 单分类本期 vs 上期金额变化。 */
data class CategoryChange(
    val tagName: String?,
    val currentCents: Long,
    val previousCents: Long,
) {
    val deltaCents: Long get() = currentCents - previousCents
}

/**
 * 报告详情用的时间纵深快照（不落库，打开详情现算）。
 */
data class ReportSeriesSnapshot(
    val points: List<PeriodTotalsPoint>,
    val mom: MomComparison,
    val topIncreases: List<CategoryChange>,
    val topDecreases: List<CategoryChange>,
)
