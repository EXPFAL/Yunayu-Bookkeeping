package com.expfal.yunayu.domain.report.model

import com.expfal.yunayu.domain.model.RecentTransaction

/**
 * 报告内某一支出分类（或「其他」桶）的详情快照。
 *
 * [remainingShares] 仅在「其他」模式下非空，为 TopN 之外的剩余分类（直播查询）。
 */
data class CategoryExpenseDetail(
    val expenseCents: Long,
    val percent: Int,
    val txCount: Int,
    val dailyAvgCents: Long,
    val recent: List<RecentTransaction>,
    val remainingShares: List<CategoryShare> = emptyList(),
)
