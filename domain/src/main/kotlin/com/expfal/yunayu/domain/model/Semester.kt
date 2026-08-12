package com.expfal.yunayu.domain.model

import java.time.LocalDate

/** 起止日期区间值对象（考试周 / 寒暑假）。 */
data class DateRange(
    val start: LocalDate,
    val endInclusive: LocalDate,
)

/**
 * 学期（PRD P0-2 / 学生专属设计 1）。
 *
 * 按学期设总预算，考试周 / 寒暑假区间用于自动切换预算策略。
 */
data class Semester(
    val id: Long = 0L,
    val name: String,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val totalBudgetCents: Long,
    val examWeekRanges: List<DateRange> = emptyList(),
    val vacationRanges: List<DateRange> = emptyList(),
)
