package com.expfal.yunayu.domain.util

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * 时间窗口纯函数集合：统一「自然月 / 自然年」统计窗口口径（systemDefault 时区，`[start, end)` 半开区间）。
 *
 * 与预算引擎口径一致：窗口端点取系统默认时区的当日零点毫秒，含起点、不含终点。本对象无副作用、
 * 无协程、无框架依赖，供预算引擎与报告生成链路共用，保证单一事实来源。
 */
object TimeWindows {

    /** 当月 1 日 00:00（系统默认时区）对应的毫秒，作为窗口含端点起点。 */
    fun monthStartMillis(today: LocalDate): Long =
        today.withDayOfMonth(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

    /**
     * 近 [days] 天窗口起点（系统默认时区）：今日零点回退 `days - 1` 天，含今日共 [days] 个自然日。
     *
     * 与 [monthStartMillis] 同用 `atStartOfDay(ZoneId.systemDefault())` 口径，保证时区处理一致；
     * `days` 必须为正整数。
     */
    fun lastNDaysStartMillis(today: LocalDate, days: Int): Long {
        require(days >= 1) { "近 N 天窗口天数必须 >= 1，实际: $days" }
        return today.minusDays((days - 1).toLong())
            .atStartOfDay(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
    }

    /** 下月 1 日 00:00（系统默认时区）对应的毫秒，作为窗口不含端终点。 */
    fun nextMonthStartMillis(today: LocalDate): Long =
        monthEnd(today).plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

    /** `today` 所在自然月的最后一天。 */
    fun monthEnd(today: LocalDate): LocalDate = today.withDayOfMonth(today.lengthOfMonth())

    /** 自然月周期键，如「2026-07」。 */
    fun monthPeriodKey(month: LocalDate): String = "%04d-%02d".format(month.year, month.monthValue)

    /** 自然年周期键，如「2026」。 */
    fun yearPeriodKey(year: Int): String = year.toString()

    /** [month] 所在自然月的完整统计窗口（含期键）。 */
    fun monthWindow(month: LocalDate): TimeWindow = TimeWindow(
        periodKey = monthPeriodKey(month),
        startInclusiveMs = monthStartMillis(month),
        endExclusiveMs = nextMonthStartMillis(month),
    )

    /** 上一月所在自然月的完整统计窗口。 */
    fun previousMonthWindow(today: LocalDate): TimeWindow = monthWindow(today.minusMonths(1))

    /** 上一年所在自然年的完整统计窗口（1/1 至次年 1/1）。 */
    fun previousYearWindow(today: LocalDate): TimeWindow = yearWindow(today.year - 1)

    /** 自然年完整统计窗口（1/1 至次年 1/1，含期键）。 */
    fun yearWindow(year: Int): TimeWindow = TimeWindow(
        periodKey = yearPeriodKey(year),
        startInclusiveMs = monthStartMillis(LocalDate.of(year, 1, 1)),
        endExclusiveMs = monthStartMillis(LocalDate.of(year + 1, 1, 1)),
    )

    /** 从自然月周期键（如「2026-07」）反推该月完整统计窗口；非法键抛 [IllegalArgumentException]。 */
    fun monthWindowByKey(periodKey: String): TimeWindow {
        val (year, month) = parseMonthKey(periodKey)
        return monthWindow(LocalDate.of(year, month, 1))
    }

    /** 从自然月周期键反推上一月完整统计窗口。 */
    fun previousMonthWindowByKey(periodKey: String): TimeWindow {
        val (year, month) = parseMonthKey(periodKey)
        return monthWindow(LocalDate.of(year, month, 1).minusMonths(1))
    }

    /** 从自然年周期键（如「2026」）反推该年完整统计窗口；非法键抛 [IllegalArgumentException]。 */
    fun yearWindowByKey(periodKey: String): TimeWindow = yearWindow(parseYearKey(periodKey))

    /** 从自然年周期键反推上一年完整统计窗口。 */
    fun previousYearWindowByKey(periodKey: String): TimeWindow = yearWindow(parseYearKey(periodKey) - 1)

    /** 将自然月周期键解析为「年、月」；非法键抛 [IllegalArgumentException]。 */
    private fun parseMonthKey(periodKey: String): Pair<Int, Int> {
        val parts = periodKey.split("-")
        require(parts.size == 2) { "非法月周期键: $periodKey" }
        val year = parts[0].toIntOrNull()
        val month = parts[1].toIntOrNull()
        require(year != null && month != null && month in 1..12) { "非法月周期键: $periodKey" }
        return year to month
    }

    /** 将自然年周期键解析为年份；非法键抛 [IllegalArgumentException]。 */
    private fun parseYearKey(periodKey: String): Int =
        periodKey.toIntOrNull() ?: throw IllegalArgumentException("非法年周期键: $periodKey")

    /**
     * 预算用的「本周 ∩ 本月」起点日期：`max(本周一, 本月 1 日)`。
     * 跨月周只从本月 1 日起算，不把上月几天摊进本月额度。
     */
    fun budgetWeekStart(today: LocalDate): LocalDate {
        val monday = today.with(DayOfWeek.MONDAY)
        val monthStart = today.withDayOfMonth(1)
        return maxOf(monday, monthStart)
    }

    /** 预算用的「本周 ∩ 本月」不含端终点日期：`min(下周一, 下月 1 日)`。 */
    fun budgetWeekEndExclusive(today: LocalDate): LocalDate {
        val nextMonday = today.with(DayOfWeek.MONDAY).plusDays(7)
        return minOf(nextMonday, monthEnd(today).plusDays(1))
    }

    fun budgetWeekStartMillis(today: LocalDate): Long =
        startOfDayMillis(budgetWeekStart(today))

    fun budgetWeekEndExclusiveMillis(today: LocalDate): Long =
        startOfDayMillis(budgetWeekEndExclusive(today))

    /** 从 [budgetWeekStart] 到月末（含）的天数，至少 1。 */
    fun daysInMonthFromBudgetWeekStart(today: LocalDate): Int =
        (ChronoUnit.DAYS.between(budgetWeekStart(today), monthEnd(today)).toInt() + 1)
            .coerceAtLeast(1)

    /**
     * 本周落在本月的天数：从 [budgetWeekStart] 到 `min(本周日, 月末)`（含）。
     * 完整周在月内时为 7；跨月周小于 7。
     */
    fun daysOfWeekInMonth(today: LocalDate): Int {
        val start = budgetWeekStart(today)
        val sunday = today.with(DayOfWeek.MONDAY).plusDays(6)
        val last = minOf(sunday, monthEnd(today))
        return (ChronoUnit.DAYS.between(start, last).toInt() + 1).coerceAtLeast(1)
    }

    private fun startOfDayMillis(date: LocalDate): Long =
        date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

    /** 本周一 00:00（系统默认时区）对应的毫秒，作为周窗口含端点起点。 */
    fun weekStartMillis(today: LocalDate): Long {
        val monday = today.with(java.time.DayOfWeek.MONDAY)
        return monday.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    }

    /** 下周一 00:00（系统默认时区）对应的毫秒，作为周窗口不含端终点。 */
    fun nextWeekStartMillis(today: LocalDate): Long {
        val nextMonday = today.with(java.time.DayOfWeek.MONDAY).plusDays(7)
        return nextMonday.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    }

    /** ISO 周周期键，如「2026-W33」。 */
    fun weekPeriodKey(today: LocalDate): String {
        val weekOfYear = today.get(java.time.temporal.WeekFields.ISO.weekOfWeekBasedYear())
        val weekBasedYear = today.get(java.time.temporal.WeekFields.ISO.weekBasedYear())
        return "%04d-W%02d".format(weekBasedYear, weekOfYear)
    }

    /** [today] 所在自然周的完整统计窗口（含期键）。 */
    fun weekWindow(today: LocalDate): TimeWindow = TimeWindow(
        periodKey = weekPeriodKey(today),
        startInclusiveMs = weekStartMillis(today),
        endExclusiveMs = nextWeekStartMillis(today),
    )

    /** 上一周的完整统计窗口。 */
    fun previousWeekWindow(today: LocalDate): TimeWindow = weekWindow(today.minusWeeks(1))

    /** 从 ISO 周周期键（如「2026-W33」）反推该周完整统计窗口；非法键抛 [IllegalArgumentException]。 */
    fun weekWindowByKey(periodKey: String): TimeWindow {
        val (year, week) = parseWeekKey(periodKey)
        val jan4 = LocalDate.of(year, 1, 4).with(java.time.DayOfWeek.MONDAY)
        val monday = jan4.plusWeeks((week - 1).toLong())
        return TimeWindow(
            periodKey = periodKey,
            startInclusiveMs = monday.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(),
            endExclusiveMs = monday.plusDays(7).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(),
        )
    }

    /** 从 ISO 周周期键反推上一周完整统计窗口。 */
    fun previousWeekWindowByKey(periodKey: String): TimeWindow {
        val (year, week) = parseWeekKey(periodKey)
        val jan4 = LocalDate.of(year, 1, 4).with(java.time.DayOfWeek.MONDAY)
        val monday = jan4.plusWeeks((week - 1).toLong())
        val prevMonday = monday.minusWeeks(1)
        return TimeWindow(
            periodKey = weekPeriodKey(prevMonday),
            startInclusiveMs = prevMonday.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(),
            endExclusiveMs = prevMonday.plusDays(7).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(),
        )
    }

    /** 将 ISO 周周期键解析为「年、周」；非法键抛 [IllegalArgumentException]。 */
    private fun parseWeekKey(periodKey: String): Pair<Int, Int> {
        val parts = periodKey.split("-W")
        require(parts.size == 2) { "非法周周期键: $periodKey" }
        val year = parts[0].toIntOrNull()
        val week = parts[1].toIntOrNull()
        require(year != null && week != null && week in 1..53) { "非法周周期键: $periodKey" }
        return year to week
    }
}

/** 一个报告周期的统计窗口：期键 + 半开时间区间 `[startInclusiveMs, endExclusiveMs)`。 */
data class TimeWindow(
    val periodKey: String,
    val startInclusiveMs: Long,
    val endExclusiveMs: Long,
)
