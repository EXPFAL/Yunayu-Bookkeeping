package com.expfal.yunayu.domain.util

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.ZoneId

/** [TimeWindows] 的 JVM 单元测试（纯函数，无协程依赖）。 */
class TimeWindowsTest {

    private fun startOfDay(date: LocalDate): Long =
        date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

    @Test
    fun `month start and next month start delimit full month window`() {
        val today = LocalDate.of(2026, 7, 15)

        assertEquals(startOfDay(LocalDate.of(2026, 7, 1)), TimeWindows.monthStartMillis(today))
        assertEquals(startOfDay(LocalDate.of(2026, 8, 1)), TimeWindows.nextMonthStartMillis(today))
    }

    @Test
    fun `last N days start is today minus N minus 1 days at start of day`() {
        val today = LocalDate.of(2026, 7, 15)

        assertEquals(startOfDay(LocalDate.of(2026, 7, 9)), TimeWindows.lastNDaysStartMillis(today, 7))
    }

    @Test
    fun `last N days start with single day equals today start of day`() {
        val today = LocalDate.of(2026, 7, 15)

        assertEquals(startOfDay(today), TimeWindows.lastNDaysStartMillis(today, 1))
    }

    @Test
    fun `last N days start crosses month boundary`() {
        val today = LocalDate.of(2026, 3, 2)

        assertEquals(startOfDay(LocalDate.of(2026, 2, 26)), TimeWindows.lastNDaysStartMillis(today, 5))
    }

    @Test
    fun `last N days start rejects non-positive days`() {
        assertThrows(IllegalArgumentException::class.java) {
            TimeWindows.lastNDaysStartMillis(LocalDate.of(2026, 7, 15), 0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            TimeWindows.lastNDaysStartMillis(LocalDate.of(2026, 7, 15), -1)
        }
    }

    @Test
    fun `month window crosses year boundary from december to january`() {
        val today = LocalDate.of(2026, 12, 31)

        assertEquals(startOfDay(LocalDate.of(2026, 12, 1)), TimeWindows.monthStartMillis(today))
        assertEquals(startOfDay(LocalDate.of(2027, 1, 1)), TimeWindows.nextMonthStartMillis(today))
    }

    @Test
    fun `period key derives month and year formats`() {
        assertEquals("2026-07", TimeWindows.monthPeriodKey(LocalDate.of(2026, 7, 1)))
        assertEquals("2026-01", TimeWindows.monthPeriodKey(LocalDate.of(2026, 1, 15)))
        assertEquals("2026", TimeWindows.yearPeriodKey(2026))
    }

    @Test
    fun `previous month window covers full previous calendar month`() {
        val today = LocalDate.of(2026, 8, 15)
        val window = TimeWindows.previousMonthWindow(today)

        assertEquals("2026-07", window.periodKey)
        assertEquals(startOfDay(LocalDate.of(2026, 7, 1)), window.startInclusiveMs)
        assertEquals(startOfDay(LocalDate.of(2026, 8, 1)), window.endExclusiveMs)
    }

    @Test
    fun `previous month window crosses year boundary`() {
        val today = LocalDate.of(2026, 1, 15)
        val window = TimeWindows.previousMonthWindow(today)

        assertEquals("2025-12", window.periodKey)
        assertEquals(startOfDay(LocalDate.of(2025, 12, 1)), window.startInclusiveMs)
        assertEquals(startOfDay(LocalDate.of(2026, 1, 1)), window.endExclusiveMs)
    }

    @Test
    fun `previous year window covers full previous calendar year`() {
        val today = LocalDate.of(2026, 1, 15)
        val window = TimeWindows.previousYearWindow(today)

        assertEquals("2025", window.periodKey)
        assertEquals(startOfDay(LocalDate.of(2025, 1, 1)), window.startInclusiveMs)
        assertEquals(startOfDay(LocalDate.of(2026, 1, 1)), window.endExclusiveMs)
    }

    @Test
    fun `month window derived from period key covers full month`() {
        val window = TimeWindows.monthWindowByKey("2026-07")

        assertEquals("2026-07", window.periodKey)
        assertEquals(startOfDay(LocalDate.of(2026, 7, 1)), window.startInclusiveMs)
        assertEquals(startOfDay(LocalDate.of(2026, 8, 1)), window.endExclusiveMs)
    }

    @Test
    fun `previous month window derived from period key crosses year boundary`() {
        val window = TimeWindows.previousMonthWindowByKey("2026-01")

        assertEquals("2025-12", window.periodKey)
        assertEquals(startOfDay(LocalDate.of(2025, 12, 1)), window.startInclusiveMs)
        assertEquals(startOfDay(LocalDate.of(2026, 1, 1)), window.endExclusiveMs)
    }

    @Test
    fun `year window derived from period key covers full year`() {
        val window = TimeWindows.yearWindowByKey("2026")

        assertEquals("2026", window.periodKey)
        assertEquals(startOfDay(LocalDate.of(2026, 1, 1)), window.startInclusiveMs)
        assertEquals(startOfDay(LocalDate.of(2027, 1, 1)), window.endExclusiveMs)
    }

    @Test
    fun `previous year window derived from period key covers prior year`() {
        val window = TimeWindows.previousYearWindowByKey("2026")

        assertEquals("2025", window.periodKey)
        assertEquals(startOfDay(LocalDate.of(2025, 1, 1)), window.startInclusiveMs)
        assertEquals(startOfDay(LocalDate.of(2026, 1, 1)), window.endExclusiveMs)
    }

    @Test
    fun `month window by malformed period key throws`() {
        assertThrows(IllegalArgumentException::class.java) { TimeWindows.monthWindowByKey("2026") }
        assertThrows(IllegalArgumentException::class.java) { TimeWindows.monthWindowByKey("2026-13") }
        assertThrows(IllegalArgumentException::class.java) { TimeWindows.monthWindowByKey("abc-def") }
    }

    @Test
    fun `year window by malformed period key throws`() {
        assertThrows(IllegalArgumentException::class.java) { TimeWindows.yearWindowByKey("2026-07") }
        assertThrows(IllegalArgumentException::class.java) { TimeWindows.yearWindowByKey("abc") }
    }

    // ===== 周相关测试 =====

    @Test
    fun `week period key uses ISO week-based year for cross-year dates`() {
        // 2027-01-01 属于 ISO 2026-W53
        assertEquals("2026-W53", TimeWindows.weekPeriodKey(LocalDate.of(2027, 1, 1)))
        // 2029-12-31 属于 ISO 2030-W01
        assertEquals("2030-W01", TimeWindows.weekPeriodKey(LocalDate.of(2029, 12, 31)))
    }

    @Test
    fun `week period key for normal mid-year date`() {
        // 2026-08-17 是周一，属于 2026-W34
        assertEquals("2026-W34", TimeWindows.weekPeriodKey(LocalDate.of(2026, 8, 17)))
    }

    @Test
    fun `week window is half-open interval monday to next monday`() {
        val today = LocalDate.of(2026, 8, 19) // 周三
        val window = TimeWindows.weekWindow(today)

        assertEquals(startOfDay(LocalDate.of(2026, 8, 17)), window.startInclusiveMs) // 周一
        assertEquals(startOfDay(LocalDate.of(2026, 8, 24)), window.endExclusiveMs) // 下周一
    }

    @Test
    fun `monday belongs to current week window`() {
        val monday = LocalDate.of(2026, 8, 17)
        val window = TimeWindows.weekWindow(monday)

        assertEquals(startOfDay(monday), window.startInclusiveMs)
        assertEquals(startOfDay(LocalDate.of(2026, 8, 24)), window.endExclusiveMs)
    }

    @Test
    fun `weekWindowByKey for 2023-W01 when jan 1 is sunday`() {
        // 2023-01-01 是周日，W01 应从 2023-01-02（周一）开始
        val window = TimeWindows.weekWindowByKey("2023-W01")

        assertEquals(startOfDay(LocalDate.of(2023, 1, 2)), window.startInclusiveMs)
        assertEquals(startOfDay(LocalDate.of(2023, 1, 9)), window.endExclusiveMs)
    }

    @Test
    fun `weekWindowByKey and weekPeriodKey are consistent`() {
        val dates = listOf(
            LocalDate.of(2026, 8, 19),
            LocalDate.of(2027, 1, 1),
            LocalDate.of(2029, 12, 31),
            LocalDate.of(2023, 1, 3),
        )
        for (date in dates) {
            val key = TimeWindows.weekPeriodKey(date)
            val window = TimeWindows.weekWindowByKey(key)
            assertEquals(key, window.periodKey)
            val ms = startOfDay(date)
            assert(ms >= window.startInclusiveMs && ms < window.endExclusiveMs) {
                "$date (ms=$ms) should be in window [${window.startInclusiveMs}, ${window.endExclusiveMs})"
            }
        }
    }

    @Test
    fun `previousWeekWindowByKey returns week before given key`() {
        val window = TimeWindows.weekWindowByKey("2026-W34")
        val prevWindow = TimeWindows.previousWeekWindowByKey("2026-W34")

        assertEquals("2026-W33", prevWindow.periodKey)
        assertEquals(window.startInclusiveMs, prevWindow.endExclusiveMs)
    }

    @Test
    fun `week window by malformed period key throws`() {
        assertThrows(IllegalArgumentException::class.java) { TimeWindows.weekWindowByKey("2026-W00") }
        assertThrows(IllegalArgumentException::class.java) { TimeWindows.weekWindowByKey("2026-W54") }
        assertThrows(IllegalArgumentException::class.java) { TimeWindows.weekWindowByKey("abc") }
        assertThrows(IllegalArgumentException::class.java) { TimeWindows.weekWindowByKey("2026") }
    }
}
