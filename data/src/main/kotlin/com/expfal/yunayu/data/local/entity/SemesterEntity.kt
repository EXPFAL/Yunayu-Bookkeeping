package com.expfal.yunayu.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 学期表（PRD P0-2）。日期以 ISO-8601 文本（yyyy-MM-dd）存储，映射层转 LocalDate。
 *
 * 考试周 / 寒暑假区间与额度均不落库（见 SCAFFOLD.md 第 5 节要点）。
 */
@Entity(tableName = "semesters")
data class SemesterEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "start_date") val startDate: String,
    @ColumnInfo(name = "end_date") val endDate: String,
    @ColumnInfo(name = "total_budget_cents") val totalBudgetCents: Long,
)
