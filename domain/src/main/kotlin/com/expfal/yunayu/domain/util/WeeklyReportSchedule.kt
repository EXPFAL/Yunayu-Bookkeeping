package com.expfal.yunayu.domain.util

import java.time.DayOfWeek
import java.time.Duration
import java.time.ZoneId
import java.time.ZonedDateTime

/** 周日复盘通知调度：对齐下个周日 10:00（系统默认时区）。 */
object WeeklyReportSchedule {

    fun millisUntilNextSunday10(
        now: ZonedDateTime = ZonedDateTime.now(ZoneId.systemDefault()),
    ): Long {
        var next = now
            .with(DayOfWeek.SUNDAY)
            .withHour(10)
            .withMinute(0)
            .withSecond(0)
            .withNano(0)
        if (!next.isAfter(now)) {
            next = next.plusWeeks(1)
        }
        return Duration.between(now, next).toMillis().coerceAtLeast(1L)
    }
}
