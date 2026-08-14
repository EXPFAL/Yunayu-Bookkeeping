package com.expfal.yunayu.domain.nl

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.ZoneId

/** [NlAmountDate] 的 JVM 单元测试。 */
class NlAmountDateTest {

    @Test
    fun `parses whole yuan to cents`() {
        assertEquals(2000L, NlAmountDate.parseAmountToCents("20"))
    }

    @Test
    fun `parses one decimal place`() {
        assertEquals(2050L, NlAmountDate.parseAmountToCents("20.5"))
    }

    @Test
    fun `parses pure fraction`() {
        assertEquals(50L, NlAmountDate.parseAmountToCents("0.5"))
    }

    @Test
    fun `rejects non numeric text`() {
        assertNull(NlAmountDate.parseAmountToCents("abc"))
    }

    @Test
    fun `rejects multiple decimal points`() {
        assertNull(NlAmountDate.parseAmountToCents("1.2.3"))
    }

    @Test
    fun `rejects empty string`() {
        assertNull(NlAmountDate.parseAmountToCents(""))
    }

    @Test
    fun `rejects overflow`() {
        assertNull(NlAmountDate.parseAmountToCents("99999999999999999"))
    }

    @Test
    fun `rejects zero or negative result`() {
        assertNull(NlAmountDate.parseAmountToCents("0"))
        assertNull(NlAmountDate.parseAmountToCents("-1"))
    }

    @Test
    fun `folds today to base time`() {
        assertEquals(NOW, NlAmountDate.parseOccurredAtEpochMillis("今天", NOW))
    }

    @Test
    fun `folds yesterday to base minus one day`() {
        assertEquals(NOW - DAY_MILLIS, NlAmountDate.parseOccurredAtEpochMillis("昨天", NOW))
    }

    @Test
    fun `folds day before yesterday to base minus two days`() {
        assertEquals(NOW - 2 * DAY_MILLIS, NlAmountDate.parseOccurredAtEpochMillis("前天", NOW))
    }

    @Test
    fun `parses ISO date to local midnight`() {
        val expected = LocalDate.parse("2026-08-15")
            .atStartOfDay(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()

        assertEquals(expected, NlAmountDate.parseOccurredAtEpochMillis("2026-08-15", NOW))
    }

    @Test
    fun `returns null for invalid or missing date`() {
        assertNull(NlAmountDate.parseOccurredAtEpochMillis("abc", NOW))
        assertNull(NlAmountDate.parseOccurredAtEpochMillis(null, NOW))
        assertNull(NlAmountDate.parseOccurredAtEpochMillis("", NOW))
        assertNull(NlAmountDate.parseOccurredAtEpochMillis("2026-13-01", NOW))
    }

    private companion object {
        const val NOW = 1_700_000_000_000L
        const val DAY_MILLIS = 24L * 60 * 60 * 1000
    }
}
