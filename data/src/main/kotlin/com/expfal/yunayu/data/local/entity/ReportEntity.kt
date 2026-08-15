package com.expfal.yunayu.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 报告表。金额一律以「分」为单位（Long）。
 *
 * `topCategories` 以「名称:cents:percent;」形式序列化（未分类名称记为空串，名称不含 `:`/`;`），
 * 反序列化失败回退空列表。`engine`/`contentVersion` 记录生成引擎与提示词版本，供未来端侧切换
 * 与手动重试识别。
 *
 * Schema v4：新增本表 + 唯一索引 `(report_type, period_key)`。
 */
@Entity(
    tableName = "reports",
    indices = [Index(value = ["report_type", "period_key"], unique = true)],
)
data class ReportEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    @ColumnInfo(name = "report_type") val reportType: String,
    @ColumnInfo(name = "period_key") val periodKey: String,
    @ColumnInfo(name = "window_start_ms") val windowStartMs: Long,
    @ColumnInfo(name = "window_end_ms") val windowEndMs: Long,
    @ColumnInfo(name = "income_cents") val incomeCents: Long,
    @ColumnInfo(name = "expense_cents") val expenseCents: Long,
    @ColumnInfo(name = "top_categories") val topCategories: String,
    @ColumnInfo(name = "prev_income_cents") val prevIncomeCents: Long,
    @ColumnInfo(name = "prev_expense_cents") val prevExpenseCents: Long,
    @ColumnInfo(name = "analysis_text") val analysisText: String?,
    @ColumnInfo(name = "status") val status: String,
    @ColumnInfo(name = "engine") val engine: String,
    @ColumnInfo(name = "content_version") val contentVersion: String,
    @ColumnInfo(name = "generated_at") val generatedAt: Long,
)
