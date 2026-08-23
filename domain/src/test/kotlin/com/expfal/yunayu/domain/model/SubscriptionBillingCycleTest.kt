package com.expfal.yunayu.domain.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.ZoneId

class SubscriptionBillingCycleTest {

    @Test
    fun `yearly amortizes to twelfth per month`() {
        assertEquals(1_000L, SubscriptionBillingCycle.YEARLY.monthlyAmortizedCents(12_000L))
    }

    @Test
    fun `quarterly amortizes to third per month`() {
        assertEquals(3_000L, SubscriptionBillingCycle.QUARTERLY.monthlyAmortizedCents(9_000L))
    }

    @Test
    fun `monthly stays unchanged`() {
        assertEquals(5_500L, SubscriptionBillingCycle.MONTHLY.monthlyAmortizedCents(5_500L))
    }

    @Test
    fun `advanceDueAt adds one month`() {
        val zone = ZoneId.of("Asia/Shanghai")
        val current = LocalDate.of(2026, 1, 15).atStartOfDay(zone).toInstant().toEpochMilli()
        val next = SubscriptionBillingCycle.MONTHLY.advanceDueAt(current, zone)
        val expected = LocalDate.of(2026, 2, 15).atStartOfDay(zone).toInstant().toEpochMilli()
        assertEquals(expected, next)
    }

    @Test
    fun `advanceDueAt adds three months for quarterly`() {
        val zone = ZoneId.of("Asia/Shanghai")
        val current = LocalDate.of(2026, 3, 1).atStartOfDay(zone).toInstant().toEpochMilli()
        val next = SubscriptionBillingCycle.QUARTERLY.advanceDueAt(current, zone)
        val expected = LocalDate.of(2026, 6, 1).atStartOfDay(zone).toInstant().toEpochMilli()
        assertEquals(expected, next)
    }

    @Test
    fun `nextUnpostedChargeDueAt returns start when never posted`() {
        val zone = ZoneId.of("Asia/Shanghai")
        val start = LocalDate.of(2026, 1, 15).atStartOfDay(zone).toInstant().toEpochMilli()
        assertEquals(
            start,
            SubscriptionBillingCycle.MONTHLY.nextUnpostedChargeDueAt(start, lastPostedDueAt = null, zone),
        )
    }

    @Test
    fun `nextUnpostedChargeDueAt skips posted periods`() {
        val zone = ZoneId.of("Asia/Shanghai")
        val start = LocalDate.of(2026, 1, 1).atStartOfDay(zone).toInstant().toEpochMilli()
        val posted = SubscriptionBillingCycle.MONTHLY.advanceDueAt(start, zone)
        assertEquals(
            SubscriptionBillingCycle.MONTHLY.advanceDueAt(posted, zone),
            SubscriptionBillingCycle.MONTHLY.nextUnpostedChargeDueAt(start, posted, zone),
        )
    }
}
