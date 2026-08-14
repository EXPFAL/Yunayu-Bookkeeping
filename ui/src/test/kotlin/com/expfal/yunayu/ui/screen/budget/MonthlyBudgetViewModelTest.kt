package com.expfal.yunayu.ui.screen.budget

import com.expfal.yunayu.domain.model.MonthlyBudgetSnapshot
import com.expfal.yunayu.domain.repository.MonthlyBudgetRepository
import com.expfal.yunayu.domain.usecase.MonthlyBudgetEngine
import com.expfal.yunayu.ui.screen.quickadd.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/** [MonthlyBudgetViewModel] 的 JVM 单元测试（手写 fake 引擎/仓储 + coroutines-test）。 */
@OptIn(ExperimentalCoroutinesApi::class)
class MonthlyBudgetViewModelTest {

    @JvmField
    @RegisterExtension
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `shows guide state when budget is zero`() = runTest {
        val viewModel = TestMonthlyBudgetViewModel(FakeMonthlyBudgetRepository(), FakeMonthlyBudgetEngine())

        val state = viewModel.uiState.value
        assertFalse(state.loading)
        assertEquals(0L, state.budgetCents)
        assertNotNull(state.snapshot)
    }

    @Test
    fun `shows snapshot when budget is set`() = runTest {
        val repo = FakeMonthlyBudgetRepository().apply { budgetFlow.value = 100_000L }
        val engine = FakeMonthlyBudgetEngine().apply { snapshot = snapshot(weeklyQuotaCents = 3_000L) }
        val viewModel = TestMonthlyBudgetViewModel(repo, engine)

        val state = viewModel.uiState.value
        assertFalse(state.loading)
        assertEquals(100_000L, state.budgetCents)
        assertEquals(3_000L, state.snapshot?.weeklyQuotaCents)
    }

    @Test
    fun `emits Saved event on successful save`() = runTest {
        val repo = FakeMonthlyBudgetRepository()
        val viewModel = TestMonthlyBudgetViewModel(repo, FakeMonthlyBudgetEngine())

        val events = mutableListOf<MonthlyBudgetEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.events.collect { events.add(it) }
        }

        viewModel.saveMonthlyBudget(50_000L)

        assertEquals(listOf(MonthlyBudgetEvent.Saved), events)
        assertEquals(50_000L, repo.savedCents)
    }

    @Test
    fun `emits SaveFailed when invalid input is rejected`() = runTest {
        val repo = FakeMonthlyBudgetRepository()
        val viewModel = TestMonthlyBudgetViewModel(repo, FakeMonthlyBudgetEngine())

        val events = mutableListOf<MonthlyBudgetEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.events.collect { events.add(it) }
        }

        viewModel.saveMonthlyBudget(0L)
        viewModel.saveMonthlyBudget(-1L)

        assertEquals(listOf(MonthlyBudgetEvent.SaveFailed, MonthlyBudgetEvent.SaveFailed), events)
        assertNull(repo.savedCents)
    }

    @Test
    fun `updates budgetCents after save`() = runTest {
        val repo = FakeMonthlyBudgetRepository()
        val viewModel = TestMonthlyBudgetViewModel(repo, FakeMonthlyBudgetEngine())

        assertEquals(0L, viewModel.uiState.value.budgetCents)

        viewModel.saveMonthlyBudget(50_000L)

        assertEquals(50_000L, viewModel.uiState.value.budgetCents)
        assertFalse(viewModel.uiState.value.saving)
    }

    @Test
    fun `observe chain failure falls back without crashing`() = runTest {
        val viewModel = TestMonthlyBudgetViewModel(
            FakeMonthlyBudgetRepository(),
            ThrowingMonthlyBudgetEngine(),
        )

        val state = viewModel.uiState.value
        assertFalse(state.loading)
        assertNull(state.snapshot)
    }

    @Test
    fun `emits SaveFailed when repository save fails`() = runTest {
        val repo = FakeMonthlyBudgetRepository().apply { saveError = RuntimeException("db down") }
        val viewModel = TestMonthlyBudgetViewModel(repo, FakeMonthlyBudgetEngine())

        val events = mutableListOf<MonthlyBudgetEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.events.collect { events.add(it) }
        }

        viewModel.saveMonthlyBudget(50_000L)

        assertEquals(listOf(MonthlyBudgetEvent.SaveFailed), events)
        assertFalse(viewModel.uiState.value.saving)
    }

    @Test
    fun `nextMidnightMillis crosses year boundary`() {
        val zone = ZoneId.systemDefault()
        val input = LocalDateTime.of(2026, 12, 31, 23, 30)
            .atZone(zone)
            .toInstant()
            .toEpochMilli()
        val expected = LocalDate.of(2027, 1, 1)
            .atStartOfDay(zone)
            .toInstant()
            .toEpochMilli()

        assertEquals(expected, nextMidnightMillis(input))
    }

    private fun snapshot(weeklyQuotaCents: Long = 0L) = MonthlyBudgetSnapshot(
        monthlyBudgetCents = 100_000L,
        spentCents = 50_000L,
        remainingCents = 50_000L,
        remainingDays = 30,
        weeklyQuotaCents = weeklyQuotaCents,
    )

    /** [MonthlyBudgetRepository] 手写 fake：以 MutableStateFlow 驱动额度，记录 save 入参并可配置异常。 */
    private class FakeMonthlyBudgetRepository : MonthlyBudgetRepository {

        val budgetFlow = MutableStateFlow(0L)
        var savedCents: Long? = null
        var saveError: Throwable? = null

        override fun observeMonthlyBudgetCents(): Flow<Long> = budgetFlow

        override suspend fun saveMonthlyBudgetCents(cents: Long) {
            saveError?.let { throw it }
            savedCents = cents
            budgetFlow.value = cents
        }
    }

    /** [MonthlyBudgetEngine] 手写 fake：返回预置快照。 */
    private class FakeMonthlyBudgetEngine : MonthlyBudgetEngine {

        var snapshot: MonthlyBudgetSnapshot = MonthlyBudgetSnapshot(
            monthlyBudgetCents = 0L,
            spentCents = 0L,
            remainingCents = 0L,
            remainingDays = 1,
            weeklyQuotaCents = 0L,
        )

        override fun observeSnapshot(today: LocalDate): Flow<MonthlyBudgetSnapshot> =
            MutableStateFlow(snapshot)
    }

    /** 观察链异常 fake：快照流立即抛错，验证 .catch 兜底不崩溃。 */
    private class ThrowingMonthlyBudgetEngine : MonthlyBudgetEngine {
        override fun observeSnapshot(today: LocalDate): Flow<MonthlyBudgetSnapshot> =
            flow { throw RuntimeException("db down") }
    }

    /** 有限 ticker 的 [MonthlyBudgetViewModel] 子类：发射一次后挂起，规避 runTest 对无限 delay 循环的挂起。 */
    private class TestMonthlyBudgetViewModel(
        repository: MonthlyBudgetRepository,
        engine: MonthlyBudgetEngine,
    ) : MonthlyBudgetViewModel(repository, engine) {
        override fun todayTicks(): Flow<Long> = flow {
            emit(System.currentTimeMillis())
            awaitCancellation()
        }
    }
}
