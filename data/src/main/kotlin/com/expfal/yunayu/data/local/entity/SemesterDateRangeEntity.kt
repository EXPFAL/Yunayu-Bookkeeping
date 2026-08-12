package com.expfal.yunayu.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 学期区间子表（Schema v2，见 SCAFFOLD.md「Schema v2 增强记录」）。
 *
 * 持久化考试周 / 假期区间，修复 [com.expfal.yunayu.data.repository.SemesterRepositoryImpl]
 * 静默丢弃 examWeekRanges / vacationRanges 导致的契约漂移。
 *
 * `rangeType` 取值见 [RANGE_TYPE_EXAM_WEEK] / [RANGE_TYPE_VACATION]；
 * 日期以 ISO-8601 文本（yyyy-MM-dd）存储，映射层转 [java.time.LocalDate]。
 */
@Entity(
    tableName = "date_ranges",
    foreignKeys = [
        ForeignKey(
            entity = SemesterEntity::class,
            parentColumns = ["id"],
            childColumns = ["semester_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("semester_id")],
)
data class SemesterDateRangeEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    @ColumnInfo(name = "semester_id") val semesterId: Long,
    @ColumnInfo(name = "range_type") val rangeType: String,
    @ColumnInfo(name = "start_date") val startDate: String,
    @ColumnInfo(name = "end_date") val endDate: String,
) {
    companion object {
        const val RANGE_TYPE_EXAM_WEEK = "EXAM_WEEK"
        const val RANGE_TYPE_VACATION = "VACATION"
    }
}
