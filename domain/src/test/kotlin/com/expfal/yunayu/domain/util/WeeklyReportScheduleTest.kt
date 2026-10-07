package com.expfal.yunayu.domain.util

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.DayOfWeek
import java.time.ZoneId
import java.time.ZonedDateTime

class WeeklyReportScheduleTest {

    @Test
    fun `next sunday 10 is after now and on sunday`() {
        val now = ZonedDateTime.of(2026, 10, 7, 20, 0, 0, 0, ZoneId.of("Asia/Shanghai"))
        val delay = WeeklyReportSchedule.millisUntilNextSunday10(now)
        val target = now.plusNanos(delay * 1_000_000L)
        assertTrue(delay > 0L)
        assertEquals(DayOfWeek.SUNDAY, target.dayOfWeek)
        assertEquals(10, target.hour)
    }

    @Test
    fun `sunday after 10 rolls to next week`() {
        val now = ZonedDateTime.of(2026, 10, 11, 11, 0, 0, 0, ZoneId.of("Asia/Shanghai"))
        val delay = WeeklyReportSchedule.millisUntilNextSunday10(now)
        val target = now.plusNanos(delay * 1_000_000L)
        assertTrue(target.toLocalDate().isAfter(now.toLocalDate()))
        assertEquals(DayOfWeek.SUNDAY, target.dayOfWeek)
    }
}
