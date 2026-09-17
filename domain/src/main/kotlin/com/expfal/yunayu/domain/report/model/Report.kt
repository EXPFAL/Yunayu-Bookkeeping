package com.expfal.yunayu.domain.report.model

/**
 * 一份周期报告。金额一律以「分」为单位。
 *
 * [localInsights] 为本地规则洞察（必有，可为空列表）；[analysisText] 为可选 LLM 点评。
 * 有结构化数据 + 本地洞察即可 [ReportStatus.SUCCESS]；无 API 不视为失败。
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
    val localInsights: List<LocalInsight> = emptyList(),
    val analysisText: String?,
    val status: ReportStatus,
    val generatedAtMs: Long,
)
