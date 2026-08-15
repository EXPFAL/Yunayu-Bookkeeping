package com.expfal.yunayu.domain.usecase

import com.expfal.yunayu.domain.model.CategoryExpense
import com.expfal.yunayu.domain.model.MonthlyBudgetSnapshot
import com.expfal.yunayu.domain.model.RecentTransaction
import com.expfal.yunayu.domain.model.Transaction
import com.expfal.yunayu.domain.model.TransactionType
import com.expfal.yunayu.domain.model.WindowTotals
import com.expfal.yunayu.domain.repository.MonthlyBudgetRepository
import com.expfal.yunayu.domain.repository.TransactionRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.ZoneId

/** [MonthlyBudgetEngineImpl] 的 JVM 单元测试（手写 fake 仓储 + MutableStateFlow 驱动）。 */
@OptIn(ExperimentalCoroutinesApi::class)
class MonthlyBudgetEngineImplTest {

    private val today = LocalDate.of(2026, 3, 15)

    @Test
    fun `snapshot covers full month window including boundary days`() = runTest {
        val transactions = MutableStateFlow(
            listOf(
                expense(1_000L, LocalDate.of(2026, 3, 1)), // 首日（含）→ 计入
                expense(2_000L, LocalDate.of(2026, 3, 31)), // 末日（含）→ 计入
                expense(3_000L, LocalDate.of(2026, 2, 28)), // 上月末 → 排除
                expense(4_000L, LocalDate.of(2026, 4, 1)), // 下月首日 → 排除
            ),
        )
        val transactionRepository = FakeTransactionRepository(transactions)
        val engine = MonthlyBudgetEngineImpl(
            FakeMonthlyBudgetRepository(MutableStateFlow(100_000L)),
            transactionRepository,
        )

        val snapshot = engine.observeSnapshot(today).first()

        assertEquals(3_000L, snapshot.spentCents)
        assertEquals(97_000L, snapshot.remainingCents)

        // 支出窗口应为 [当月 1 日 00:00, 下月 1 日 00:00)
        val (start, end) = transactionRepository.expenseSumWindows.single()
        assertEquals(startOfDayMillis(LocalDate.of(2026, 3, 1)), start)
        assertEquals(startOfDayMillis(LocalDate.of(2026, 4, 1)), end)
    }

    @Test
    fun `excludes income transactions from spent`() = runTest {
        val transactions = MutableStateFlow(
            listOf(
                expense(10_000L, LocalDate.of(2026, 3, 10)),
                income(99_000L, LocalDate.of(2026, 3, 12)),
            ),
        )
        val engine = MonthlyBudgetEngineImpl(
            FakeMonthlyBudgetRepository(MutableStateFlow(100_000L)),
            FakeTransactionRepository(transactions),
        )

        val snapshot = engine.observeSnapshot(today).first()

        assertEquals(10_000L, snapshot.spentCents)
    }

    @Test
    fun `clamps remaining to zero when spent exceeds budget`() = runTest {
        val transactions = MutableStateFlow(listOf(expense(150_000L, LocalDate.of(2026, 3, 10))))
        val engine = MonthlyBudgetEngineImpl(
            FakeMonthlyBudgetRepository(MutableStateFlow(100_000L)),
            FakeTransactionRepository(transactions),
        )

        val snapshot = engine.observeSnapshot(today).first()

        assertEquals(150_000L, snapshot.spentCents)
        assertEquals(0L, snapshot.remainingCents)
        assertEquals(0L, snapshot.weeklyQuotaCents)
    }

    @Test
    fun `clamps remainingDays to one when today is month end`() = runTest {
        val engine = MonthlyBudgetEngineImpl(
            FakeMonthlyBudgetRepository(MutableStateFlow(100_000L)),
            FakeTransactionRepository(MutableStateFlow(emptyList())),
        )

        val snapshot = engine.observeSnapshot(LocalDate.of(2026, 3, 31)).first()

        assertEquals(1, snapshot.remainingDays)
    }

    @Test
    fun `weekly quota uses integer division semantics`() = runTest {
        val engine = MonthlyBudgetEngineImpl(
            FakeMonthlyBudgetRepository(MutableStateFlow(100_000L)),
            FakeTransactionRepository(MutableStateFlow(emptyList())),
        )

        // today = 3/1，remainingDays = 31；100_000 * 7 / 31 = 700_000 / 31 = 22_580
        val snapshot = engine.observeSnapshot(LocalDate.of(2026, 3, 1)).first()

        assertEquals(31, snapshot.remainingDays)
        assertEquals(22_580L, snapshot.weeklyQuotaCents)
    }

    @Test
    fun `emits spent normally when budget not set`() = runTest {
        val transactions = MutableStateFlow(listOf(expense(5_000L, LocalDate.of(2026, 3, 8))))
        val engine = MonthlyBudgetEngineImpl(
            FakeMonthlyBudgetRepository(MutableStateFlow(0L)),
            FakeTransactionRepository(transactions),
        )

        val snapshot = engine.observeSnapshot(today).first()

        assertEquals(0L, snapshot.monthlyBudgetCents)
        assertEquals(5_000L, snapshot.spentCents)
        assertEquals(0L, snapshot.remainingCents)
        assertEquals(0L, snapshot.weeklyQuotaCents)
    }

    @Test
    fun `re-emits with updated spent when expense appended`() = runTest {
        val transactions = MutableStateFlow<List<Transaction>>(emptyList())
        val engine = MonthlyBudgetEngineImpl(
            FakeMonthlyBudgetRepository(MutableStateFlow(100_000L)),
            FakeTransactionRepository(transactions),
        )

        var emissions: List<MonthlyBudgetSnapshot> = emptyList()
        val job = launch {
            emissions = engine.observeSnapshot(today).take(2).toList()
        }

        advanceUntilIdle()
        transactions.value = listOf(expense(2_000L, LocalDate.of(2026, 3, 15)))
        advanceUntilIdle()
        job.join()

        assertEquals(2, emissions.size)
        assertEquals(0L, emissions[0].spentCents)
        assertEquals(2_000L, emissions[1].spentCents)
    }

    private fun expense(amountCents: Long, date: LocalDate): Transaction = Transaction(
        amountCents = amountCents,
        type = TransactionType.EXPENSE,
        occurredAt = startOfDayMillis(date),
    )

    private fun income(amountCents: Long, date: LocalDate): Transaction = Transaction(
        amountCents = amountCents,
        type = TransactionType.INCOME,
        occurredAt = startOfDayMillis(date),
    )

    private fun startOfDayMillis(date: LocalDate): Long =
        date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

    /** [MonthlyBudgetRepository] 手写 fake：以 MutableStateFlow 驱动预算流。 */
    private class FakeMonthlyBudgetRepository(
        private val budget: MutableStateFlow<Long>,
    ) : MonthlyBudgetRepository {

        override fun observeMonthlyBudgetCents(): Flow<Long> = budget

        override suspend fun saveMonthlyBudgetCents(cents: Long) {
            budget.value = cents
        }
    }

    /**
     * [TransactionRepository] 手写 fake：以 MutableStateFlow 驱动交易列表，并仿照 DAO 语义
     * 在 [observeExpenseSumBetween] 中按窗口过滤支出、排除收入，同时记录窗口入参供断言。
     */
    private class FakeTransactionRepository(
        private val transactions: MutableStateFlow<List<Transaction>>,
    ) : TransactionRepository {

        val expenseSumWindows = mutableListOf<Pair<Long, Long>>()

        override suspend fun add(transaction: Transaction): Long = 0L

        override suspend fun delete(transactionId: Long) = Unit

        override fun observeAll(): Flow<List<Transaction>> = transactions

        override fun observeByTag(tagId: Long): Flow<List<Transaction>> = flowOf(emptyList())

        override fun observeExpenseSumBetween(
            startInclusiveMs: Long,
            endExclusiveMs: Long,
        ): Flow<Long> {
            expenseSumWindows += startInclusiveMs to endExclusiveMs
            return transactions.map { list ->
                list.filter { it.type == TransactionType.EXPENSE }
                    .filter { it.occurredAt >= startInclusiveMs && it.occurredAt < endExclusiveMs }
                    .sumOf { it.amountCents }
            }
        }

        override fun observeHeldCents(): Flow<Long> = flowOf(0L)

        override suspend fun getWindowTotals(
            startInclusiveMs: Long,
            endExclusiveMs: Long,
        ): WindowTotals = WindowTotals(0L, 0L)

        override suspend fun getExpenseByCategory(
            startInclusiveMs: Long,
            endExclusiveMs: Long,
        ): List<CategoryExpense> = emptyList()

        override fun observeRecent(limit: Int): Flow<List<RecentTransaction>> =
            flowOf(emptyList())

        override fun observeFiltered(
            startInclusiveMs: Long?,
            endExclusiveMs: Long?,
            tagIds: List<Long>,
            noteKeyword: String?,
        ): Flow<List<RecentTransaction>> = flowOf(emptyList())
    }
}
