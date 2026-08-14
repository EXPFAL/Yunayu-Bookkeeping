package com.expfal.yunayu.ui.util

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** [formatCents] 千分位/两位小数/零值/大额的 JVM 单元测试。 */
class AmountFormatTest {

    @Test
    fun `formats with thousands separator and two decimals`() {
        assertEquals("1,234.56", formatCents(123_456L))
        assertEquals("0.05", formatCents(5L))
        assertEquals("0.00", formatCents(0L))
        assertEquals("10,000.00", formatCents(1_000_000L))
        assertEquals("123,456,789.12", formatCents(12_345_678_912L))
    }
}
