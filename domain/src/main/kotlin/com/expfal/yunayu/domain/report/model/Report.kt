package com.expfal.yunayu.domain.report.model

/**
 * 一份月度 / 年度报告。金额一律以「分」为单位。
 *
 * `topCategories` 为当期支出按金额降序的前 N 个分类占比；`analysisText` 为引擎生成的分析文本，
 * 生成失败时为 `null` 且 [status] 为 [ReportStatus.FAILED]（结构化数据仍在，可手动重试）。
 */
data class Report(
    val id: Long = 0L,
    val periodType: ReportPeriodType,
    val periodKey: String,
    val windowStartMs: Long,
    val windowEndMs: Long,
    val incomeCents: Long,
    val expenseCents: Long,
    val topCategories: List<CategoryShare>,
    val prevIncomeCents: Long,
    val prevExpenseCents: Long,
    val analysisText: String?,
    val status: ReportStatus,
    val generatedAtMs: Long,
)
