package com.expfal.yunayu.domain.util

import java.time.LocalDate
import java.time.ZoneId

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
}

/** 一个报告周期的统计窗口：期键 + 半开时间区间 `[startInclusiveMs, endExclusiveMs)`。 */
data class TimeWindow(
    val periodKey: String,
    val startInclusiveMs: Long,
    val endExclusiveMs: Long,
)
