package com.expfal.yunayu.domain.usecase

import com.expfal.yunayu.domain.model.BudgetPhase
import com.expfal.yunayu.domain.model.BudgetSnapshot
import com.expfal.yunayu.domain.model.DateRange
import com.expfal.yunayu.domain.model.Semester
import com.expfal.yunayu.domain.model.Transaction
import com.expfal.yunayu.domain.model.TransactionType
import com.expfal.yunayu.domain.repository.SemesterRepository
import com.expfal.yunayu.domain.repository.TransactionRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.ZoneId

/** [SemesterBudgetEngineImpl] 的 JVM 单元测试（手写 fake 仓储 + MutableStateFlow 驱动）。 */
@OptIn(ExperimentalCoroutinesApi::class)
class SemesterBudgetEngineImplTest {

    @Test
    fun `weekly quota applies integer division semantics for normal phase`() {
        val engine = newEngine()

        // NORMAL pct=100：100_000 * 7 * 100 / 30 / 100 = 70_000_000 / 30 / 100 = 2_333_333 / 100
        assertEquals(23_333L, engine.calcWeeklyQuota(100_000L, 30, BudgetPhase.NORMAL))

        // 非整除场景逐级向下取整：1_000 * 7 * 100 / 30 / 100 = 700_000 / 30 / 100 = 23_333 / 100
        assertEquals(233L, engine.calcWeeklyQuota(1_000L, 30, BudgetPhase.NORMAL))
    }

    @Test
    fun `exam week and vacation coefficients adjust quotas`() {
        val engine = newEngine()

        // 考试周 0.8：100_000 * 7 * 80 / 30 / 100 = 56_000_000 / 30 / 100 = 1_866_666 / 100
        assertEquals(18_666L, engine.calcWeeklyQuota(100_000L, 30, BudgetPhase.EXAM_WEEK))
        // 假期 1.1：100_000 * 7 * 110 / 30 / 100 = 77_000_000 / 30 / 100 = 2_566_666 / 100
        assertEquals(25_666L, engine.calcWeeklyQuota(100_000L, 30, BudgetPhase.VACATION))
        // 月额度假期 1.1：100_000 * 30 * 110 / 30 / 100 = 330_000_000 / 30 / 100 = 11_000_000 / 100
        assertEquals(110_000L, engine.calcMonthlyQuota(100_000L, 30, BudgetPhase.VACATION))
    }

    @Test
    fun `resolvePhase handles inside outside boundary and overlap precedence`() {
        val engine = newEngine()
        val semester = semester(
            examRanges = listOf(DateRange(LocalDate.of(2026, 6, 15), LocalDate.of(2026, 6, 28))),
            vacationRanges = listOf(DateRange(LocalDate.of(2026, 6, 20), LocalDate.of(2026, 8, 31))),
        )

        assertEquals(BudgetPhase.NORMAL, engine.resolvePhase(semester, LocalDate.of(2026, 3, 15)))
        // 考试周边界日（起止当天）
        assertEquals(BudgetPhase.EXAM_WEEK, engine.resolvePhase(semester, LocalDate.of(2026, 6, 15)))
        assertEquals(BudgetPhase.EXAM_WEEK, engine.resolvePhase(semester, LocalDate.of(2026, 6, 28)))
        // 考试周与假期重叠 → 考试周优先
        assertEquals(BudgetPhase.EXAM_WEEK, engine.resolvePhase(semester, LocalDate.of(2026, 6, 22)))
        // 仅落在假期区间
        assertEquals(BudgetPhase.VACATION, engine.resolvePhase(semester, LocalDate.of(2026, 7, 15)))
    }

    @Test
    fun `snapshot sums only in-range expenses`() = runTest {
        val semesterFlow = MutableStateFlow(
            listOf(semester(start = LocalDate.of(2026, 3, 1), end = LocalDate.of(2026, 3, 31))),
        )
        val transactionFlow = MutableStateFlow(
            listOf(
                expense(10_000L, LocalDate.of(2026, 3, 1)), // 起始日（含）→ 计入
                expense(5_000L, LocalDate.of(2026, 3, 31)), // 结束日（含）→ 计入
                expense(3_000L, LocalDate.of(2026, 2, 28)), // 区间外（早于起始）→ 排除
                expense(4_000L, LocalDate.of(2026, 4, 1)), // 区间外（晚于结束）→ 排除
                income(99_000L, LocalDate.of(2026, 3, 15)), // INCOME → 排除
            ),
        )
        val engine = SemesterBudgetEngineImpl(
            FakeSemesterRepository(semesterFlow),
            FakeTransactionRepository(transactionFlow),
        )

        val snapshot = engine.observeBudgetSnapshot(1L, LocalDate.of(2026, 3, 1)).first()

        assertEquals(100_000L, snapshot.totalBudgetCents)
        assertEquals(15_000L, snapshot.spentCents)
        assertEquals(85_000L, snapshot.remainingCents)
        assertEquals(31, snapshot.remainingDays)
        assertEquals(BudgetPhase.NORMAL, snapshot.phase)
    }

    @Test
    fun `snapshot clamps remaining to zero when spent exceeds budget`() = runTest {
        val semesterFlow = MutableStateFlow(
            listOf(semester(start = LocalDate.of(2026, 3, 1), end = LocalDate.of(2026, 3, 31))),
        )
        val transactionFlow = MutableStateFlow(
            listOf(expense(150_000L, LocalDate.of(2026, 3, 10))),
        )
        val engine = SemesterBudgetEngineImpl(
            FakeSemesterRepository(semesterFlow),
            FakeTransactionRepository(transactionFlow),
        )

        val snapshot = engine.observeBudgetSnapshot(1L, LocalDate.of(2026, 3, 1)).first()

        assertEquals(150_000L, snapshot.spentCents)
        assertEquals(0L, snapshot.remainingCents)
        assertEquals(0L, snapshot.weeklyQuotaCents)
        assertEquals(0L, snapshot.monthlyQuotaCents)
    }

    @Test
    fun `snapshot clamps remainingDays to one when today is after endDate`() = runTest {
        val semesterFlow = MutableStateFlow(
            listOf(semester(start = LocalDate.of(2026, 3, 1), end = LocalDate.of(2026, 3, 31))),
        )
        val engine = SemesterBudgetEngineImpl(
            FakeSemesterRepository(semesterFlow),
            FakeTransactionRepository(MutableStateFlow(emptyList())),
        )

        val snapshot = engine.observeBudgetSnapshot(1L, LocalDate.of(2026, 4, 10)).first()

        assertEquals(1, snapshot.remainingDays)
    }

    @Test
    fun `snapshot emits zero snapshot when semester not found`() = runTest {
        val engine = SemesterBudgetEngineImpl(
            FakeSemesterRepository(MutableStateFlow(emptyList())),
            FakeTransactionRepository(
                MutableStateFlow(listOf(expense(5_000L, LocalDate.of(2026, 3, 10)))),
            ),
        )

        val snapshot = engine.observeBudgetSnapshot(999L, LocalDate.of(2026, 3, 15)).first()

        assertEquals(
            BudgetSnapshot(
                totalBudgetCents = 0L,
                spentCents = 0L,
                remainingCents = 0L,
                remainingDays = 0,
                weeklyQuotaCents = 0L,
                monthlyQuotaCents = 0L,
                phase = BudgetPhase.NORMAL,
            ),
            snapshot,
        )
    }

    @Test
    fun `snapshot re-emits with updated spent when expense appended`() = runTest {
        val semesterFlow = MutableStateFlow(
            listOf(semester(start = LocalDate.of(2026, 3, 1), end = LocalDate.of(2026, 3, 31))),
        )
        val transactionFlow = MutableStateFlow<List<Transaction>>(emptyList())
        val engine = SemesterBudgetEngineImpl(
            FakeSemesterRepository(semesterFlow),
            FakeTransactionRepository(transactionFlow),
        )

        var emissions: List<BudgetSnapshot> = emptyList()
        val job = launch {
            emissions = engine
                .observeBudgetSnapshot(semesterId = 1L, today = LocalDate.of(2026, 3, 1))
                .take(2)
                .toList()
        }

        advanceUntilIdle()
        transactionFlow.value = listOf(expense(2_000L, LocalDate.of(2026, 3, 15)))
        advanceUntilIdle()
        job.join()

        assertEquals(2, emissions.size)
        assertEquals(0L, emissions[0].spentCents)
        assertEquals(2_000L, emissions[1].spentCents)
    }

    private fun semester(
        id: Long = 1L,
        start: LocalDate = LocalDate.of(2026, 3, 1),
        end: LocalDate = LocalDate.of(2026, 7, 31),
        budget: Long = 100_000L,
        examRanges: List<DateRange> = emptyList(),
        vacationRanges: List<DateRange> = emptyList(),
    ): Semester = Semester(
        id = id,
        name = "2026 春季学期",
        startDate = start,
        endDate = end,
        totalBudgetCents = budget,
        examWeekRanges = examRanges,
        vacationRanges = vacationRanges,
    )

    private fun expense(amountCents: Long, date: LocalDate): Transaction = Transaction(
        amountCents = amountCents,
        type = TransactionType.EXPENSE,
        occurredAt = date.startOfDayMillis(),
    )

    private fun income(amountCents: Long, date: LocalDate): Transaction = Transaction(
        amountCents = amountCents,
        type = TransactionType.INCOME,
        occurredAt = date.startOfDayMillis(),
    )

    private fun LocalDate.startOfDayMillis(): Long =
        atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

    private fun newEngine(
        semesters: List<Semester> = emptyList(),
        transactions: List<Transaction> = emptyList(),
    ): SemesterBudgetEngineImpl = SemesterBudgetEngineImpl(
        FakeSemesterRepository(MutableStateFlow(semesters)),
        FakeTransactionRepository(MutableStateFlow(transactions)),
    )

    /** [SemesterRepository] 手写 fake：以 MutableStateFlow 驱动观察流。 */
    private class FakeSemesterRepository(
        private val semesters: MutableStateFlow<List<Semester>>,
    ) : SemesterRepository {

        override suspend fun save(semester: Semester): Long = 0L

        override fun observeAll(): Flow<List<Semester>> = semesters
    }

    /** [TransactionRepository] 手写 fake：以 MutableStateFlow 驱动观察流。 */
    private class FakeTransactionRepository(
        private val transactions: MutableStateFlow<List<Transaction>>,
    ) : TransactionRepository {

        override suspend fun add(transaction: Transaction): Long = 0L

        override fun observeAll(): Flow<List<Transaction>> = transactions

        override fun observeByTag(tagId: Long): Flow<List<Transaction>> = flowOf(emptyList())
    }
}
