package com.expfal.yunayu.ui.util

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/** 预算金额输入过滤与解析纯函数的 JVM 单元测试。 */
class BudgetInputTest {

    @Test
    fun `filter keeps only ascii digits and one dot with limits`() {
        assertEquals("123", filterBudgetInput("123"))
        assertEquals("0.50", filterBudgetInput(".50"))
        assertEquals("12.34", filterBudgetInput("12.345"))
        assertEquals("1.23", filterBudgetInput("1.2.3"))
        assertEquals("123456789", filterBudgetInput("123456789012"))
        assertEquals("12", filterBudgetInput("12abc"))
        assertEquals("12", filterBudgetInput("12٣"))
    }

    @Test
    fun `parse returns cents for valid input`() {
        assertEquals(123L, parseBudgetToCents("1.23"))
        assertEquals(5L, parseBudgetToCents("0.05"))
        assertEquals(100L, parseBudgetToCents("1"))
        assertEquals(100L, parseBudgetToCents("1.0"))
    }

    @Test
    fun `parse rejects invalid or non positive input`() {
        assertNull(parseBudgetToCents(""))
        assertNull(parseBudgetToCents("0"))
        assertNull(parseBudgetToCents("0.00"))
        assertNull(parseBudgetToCents("."))
        assertNull(parseBudgetToCents("1.2.3"))
        assertNull(parseBudgetToCents("1.234"))
        assertNull(parseBudgetToCents("١٢٣"))
        assertNull(parseBudgetToCents("١.٢٣"))
        assertNull(parseBudgetToCents("12345678901234567890"))
    }

    @Test
    fun `parse initial balance returns cents allowing zero`() {
        assertEquals(0L, parseInitialBalanceToCents(""))
        assertEquals(0L, parseInitialBalanceToCents("0"))
        assertEquals(0L, parseInitialBalanceToCents("0.00"))
        assertEquals(123L, parseInitialBalanceToCents("1.23"))
        assertEquals(5L, parseInitialBalanceToCents("0.05"))
        assertEquals(100L, parseInitialBalanceToCents("1"))
        assertEquals(100L, parseInitialBalanceToCents("1.0"))
    }

    @Test
    fun `parse initial balance rejects invalid or negative input`() {
        assertNull(parseInitialBalanceToCents("."))
        assertNull(parseInitialBalanceToCents("1.2.3"))
        assertNull(parseInitialBalanceToCents("1.234"))
        assertNull(parseInitialBalanceToCents("-5"))
        assertNull(parseInitialBalanceToCents("١٢٣"))
        assertNull(parseInitialBalanceToCents("12345678901234567890"))
    }

    @Test
    fun `cents to initial balance text omits trailing zeros and zero`() {
        assertEquals("", centsToInitialBalanceText(0L))
        assertEquals("", centsToInitialBalanceText(-1L))
        assertEquals("100", centsToInitialBalanceText(10_000L))
        assertEquals("1.23", centsToInitialBalanceText(123L))
    }
}
