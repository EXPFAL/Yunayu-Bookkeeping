package com.expfal.yunayu.ui.util

import com.expfal.yunayu.domain.model.TransactionType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** [formatCents] 千分位/两位小数/零值/大额与 [formatSignedCents] 方向符号的 JVM 单元测试。 */
class AmountFormatTest {

    @Test
    fun `formats with thousands separator and two decimals`() {
        assertEquals("1,234.56", formatCents(123_456L))
        assertEquals("0.05", formatCents(5L))
        assertEquals("0.00", formatCents(0L))
        assertEquals("10,000.00", formatCents(1_000_000L))
        assertEquals("123,456,789.12", formatCents(12_345_678_912L))
    }

    @Test
    fun `formatSignedCents prefixes income with plus and expense with minus`() {
        assertEquals("+1,234.56", formatSignedCents(123_456L, TransactionType.INCOME))
        assertEquals("-1,234.56", formatSignedCents(123_456L, TransactionType.EXPENSE))
    }

    @Test
    fun `formatSignedCents handles zero and large amounts`() {
        assertEquals("+0.00", formatSignedCents(0L, TransactionType.INCOME))
        assertEquals("-0.00", formatSignedCents(0L, TransactionType.EXPENSE))
        assertEquals("+123,456,789.12", formatSignedCents(12_345_678_912L, TransactionType.INCOME))
    }
}
