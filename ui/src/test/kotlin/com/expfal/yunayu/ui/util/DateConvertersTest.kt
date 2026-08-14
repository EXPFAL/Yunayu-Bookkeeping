package com.expfal.yunayu.ui.util

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.LocalDate

/** UTC 毫秒 ↔ LocalDate 换算的 JVM 单元测试（不依赖设备时区）。 */
class DateConvertersTest {

    @Test
    fun `toUtcEpochMillis anchors to utc midnight not system default`() {
        // 1970-01-02 的 UTC 零点 = 86_400_000；若误用东八区零点会得到 57_600_000，据此固定断言
        assertEquals(86_400_000L, LocalDate.of(1970, 1, 2).toUtcEpochMillis())
    }

    @Test
    fun `toUtcLocalDate anchors to utc midnight not system default`() {
        assertEquals(LocalDate.of(1970, 1, 2), 86_400_000L.toUtcLocalDate())
    }

    @Test
    fun `round trip preserves date regardless of device timezone`() {
        val dates = listOf(
            LocalDate.of(1970, 1, 2),
            LocalDate.of(2000, 2, 29),
            LocalDate.of(2026, 8, 14),
        )
        dates.forEach { date ->
            assertEquals(date, date.toUtcEpochMillis().toUtcLocalDate())
        }
    }
}
