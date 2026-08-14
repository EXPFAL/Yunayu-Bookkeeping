package com.expfal.yunayu.ui.screen.budget

import com.expfal.yunayu.domain.model.BudgetPhase
import com.expfal.yunayu.domain.model.BudgetSnapshot
import com.expfal.yunayu.domain.model.DateRange
import com.expfal.yunayu.domain.model.Semester
import com.expfal.yunayu.domain.repository.SemesterRepository
import com.expfal.yunayu.domain.usecase.SemesterBudgetEngine
import com.expfal.yunayu.ui.screen.quickadd.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.time.LocalDate

/** [BudgetViewModel] 的 JVM 单元测试（手写 fake 引擎/仓储 + coroutines-test）。 */
@OptIn(ExperimentalCoroutinesApi::class)
class BudgetViewModelTest {

    @JvmField
    @RegisterExtension
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `shows guide state when no active semester`() = runTest {
        val repo = FakeSemesterRepository().apply { activeSemester = null }
        val viewModel = BudgetViewModel(FakeBudgetEngine(), repo)

        val state = viewModel.uiState.value
        assertFalse(state.loading)
        assertNull(state.semester)
        assertNull(state.snapshot)
    }

    @Test
    fun `shows snapshot when active semester exists`() = runTest {
        val semester = semester(id = 5L)
        val repo = FakeSemesterRepository().apply { activeSemester = semester }
        val engine = FakeBudgetEngine().apply { snapshot = snapshot(weeklyQuotaCents = 3_000L) }
        val viewModel = BudgetViewModel(engine, repo)

        val state = viewModel.uiState.value
        assertFalse(state.loading)
        assertEquals(semester, state.semester)
        assertEquals(3_000L, state.snapshot?.weeklyQuotaCents)
    }

    @Test
    fun `saves new semester with zero id and empty ranges`() = runTest {
        val repo = FakeSemesterRepository().apply { activeSemester = null }
        val viewModel = BudgetViewModel(FakeBudgetEngine(), repo)

        viewModel.saveSemester(
            name = "2026 秋季学期",
            startDate = LocalDate.of(2026, 9, 1),
            endDate = LocalDate.of(2027, 1, 15),
            totalBudgetCents = 1_000_000L,
        )

        assertEquals(1, repo.saved.size)
        val saved = repo.saved.single()
        assertEquals(0L, saved.id)
        assertEquals("2026 秋季学期", saved.name)
        assertEquals(LocalDate.of(2026, 9, 1), saved.startDate)
        assertEquals(LocalDate.of(2027, 1, 15), saved.endDate)
        assertEquals(1_000_000L, saved.totalBudgetCents)
        assertTrue(saved.examWeekRanges.isEmpty())
        assertTrue(saved.vacationRanges.isEmpty())
    }

    @Test
    fun `editing keeps id and existing ranges`() = runTest {
        val exam = DateRange(LocalDate.of(2026, 12, 1), LocalDate.of(2026, 12, 7))
        val vacation = DateRange(LocalDate.of(2027, 1, 1), LocalDate.of(2027, 1, 3))
        val repo = FakeSemesterRepository().apply {
            activeSemester = semester(id = 5L, exam = listOf(exam), vacation = listOf(vacation))
        }
        val viewModel = BudgetViewModel(FakeBudgetEngine(), repo)

        viewModel.saveSemester(
            name = "改名学期",
            startDate = LocalDate.of(2026, 9, 10),
            endDate = LocalDate.of(2027, 1, 10),
            totalBudgetCents = 2_000_000L,
        )

        val saved = repo.saved.single()
        assertEquals(5L, saved.id)
        assertEquals("改名学期", saved.name)
        assertEquals(listOf(exam), saved.examWeekRanges)
        assertEquals(listOf(vacation), saved.vacationRanges)
    }

    @Test
    fun `emits SaveFailed when repository save fails`() = runTest {
        val repo = FakeSemesterRepository().apply {
            activeSemester = null
            saveError = RuntimeException("db down")
        }
        val viewModel = BudgetViewModel(FakeBudgetEngine(), repo)

        val events = mutableListOf<BudgetEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.events.collect { events.add(it) }
        }

        viewModel.saveSemester(
            name = "2026 秋季学期",
            startDate = LocalDate.of(2026, 9, 1),
            endDate = LocalDate.of(2027, 1, 15),
            totalBudgetCents = 100_000L,
        )

        assertEquals(listOf(BudgetEvent.SaveFailed), events)
    }

    @Test
    fun `invalid input does not persist`() = runTest {
        val repo = FakeSemesterRepository().apply { activeSemester = null }
        val viewModel = BudgetViewModel(FakeBudgetEngine(), repo)

        viewModel.saveSemester("", LocalDate.of(2026, 9, 1), LocalDate.of(2027, 1, 15), 100_000L)
        viewModel.saveSemester("学期", LocalDate.of(2026, 9, 1), LocalDate.of(2026, 8, 1), 100_000L)
        viewModel.saveSemester("学期", LocalDate.of(2026, 9, 1), LocalDate.of(2027, 1, 15), 0L)

        assertTrue(repo.saved.isEmpty())
    }

    private fun semester(
        id: Long = 0L,
        exam: List<DateRange> = listOf(DateRange(LocalDate.of(2026, 12, 1), LocalDate.of(2026, 12, 7))),
        vacation: List<DateRange> = listOf(DateRange(LocalDate.of(2027, 1, 1), LocalDate.of(2027, 1, 3))),
    ) = Semester(
        id = id,
        name = "2026 秋季学期",
        startDate = LocalDate.of(2026, 9, 1),
        endDate = LocalDate.of(2027, 1, 15),
        totalBudgetCents = 1_000_000L,
        examWeekRanges = exam,
        vacationRanges = vacation,
    )

    private fun snapshot(weeklyQuotaCents: Long = 0L) = BudgetSnapshot(
        totalBudgetCents = 1_000_000L,
        spentCents = 500_000L,
        remainingCents = 500_000L,
        remainingDays = 30,
        weeklyQuotaCents = weeklyQuotaCents,
        monthlyQuotaCents = 0L,
        phase = BudgetPhase.NORMAL,
    )

    /** [SemesterRepository] 手写 fake：直接返回预置激活学期，记录 save 入参并可配置异常。 */
    private class FakeSemesterRepository : SemesterRepository {

        var activeSemester: Semester? = null
        val saved = mutableListOf<Semester>()
        var saveError: Throwable? = null
        var nextId: Long = 10L

        override suspend fun save(semester: Semester): Long {
            saveError?.let { throw it }
            saved += semester
            return if (semester.id == 0L) nextId else semester.id
        }

        override fun observeAll(): Flow<List<Semester>> = flowOf(emptyList())

        override fun observeById(id: Long): Flow<Semester?> = flowOf(null)

        override fun observeActiveSemester(todayEpochMillis: Long): Flow<Semester?> = flowOf(activeSemester)
    }

    /** [SemesterBudgetEngine] 手写 fake：返回预置快照，其余方法返回默认值。 */
    private class FakeBudgetEngine : SemesterBudgetEngine {

        var snapshot: BudgetSnapshot = BudgetSnapshot(
            totalBudgetCents = 0L,
            spentCents = 0L,
            remainingCents = 0L,
            remainingDays = 0,
            weeklyQuotaCents = 0L,
            monthlyQuotaCents = 0L,
            phase = BudgetPhase.NORMAL,
        )

        override fun observeBudgetSnapshot(semesterId: Long, today: LocalDate): Flow<BudgetSnapshot> =
            flowOf(snapshot)

        override fun calcWeeklyQuota(remainingCents: Long, remainingDays: Int, phase: BudgetPhase): Long = 0L

        override fun calcMonthlyQuota(remainingCents: Long, remainingDays: Int, phase: BudgetPhase): Long = 0L

        override fun resolvePhase(semester: Semester, date: LocalDate): BudgetPhase = BudgetPhase.NORMAL
    }
}
