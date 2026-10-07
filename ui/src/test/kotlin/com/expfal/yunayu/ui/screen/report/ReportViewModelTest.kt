package com.expfal.yunayu.ui.screen.report

import com.expfal.yunayu.domain.model.AccountFilter
import com.expfal.yunayu.domain.model.CategoryExpense
import com.expfal.yunayu.domain.model.MonthlyBudgetSnapshot
import com.expfal.yunayu.domain.model.RecentTransaction
import com.expfal.yunayu.domain.model.Transaction
import com.expfal.yunayu.domain.model.WindowTotals
import com.expfal.yunayu.domain.nl.NLTransactionParser
import com.expfal.yunayu.domain.report.DeepReadReportUseCase
import com.expfal.yunayu.domain.report.EnsureReportsUseCase
import com.expfal.yunayu.domain.report.GenerateReportUseCase
import com.expfal.yunayu.domain.report.LoadReportSeriesUseCase
import com.expfal.yunayu.domain.report.model.CategoryShare
import com.expfal.yunayu.domain.report.model.Report
import com.expfal.yunayu.domain.report.model.ReportPeriodType
import com.expfal.yunayu.domain.report.model.ReportStatus
import com.expfal.yunayu.domain.repository.MonthlyBudgetRepository
import com.expfal.yunayu.domain.repository.ReportRepository
import com.expfal.yunayu.domain.repository.TransactionRepository
import com.expfal.yunayu.domain.usecase.MonthlyBudgetEngine
import com.expfal.yunayu.domain.util.TimeWindows
import com.expfal.yunayu.ui.screen.quickadd.MainDispatcherRule
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.time.LocalDate
import java.time.ZoneId

/** [ReportViewModel] 的 JVM 单元测试（手写 fake 仓储/引擎 + coroutines-test）。 */
@OptIn(ExperimentalCoroutinesApi::class)
class ReportViewModelTest {

    @JvmField
    @RegisterExtension
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `observes weekly reports by default`() = runTest {
        val weekKey = TimeWindows.weekPeriodKey(LocalDate.now())
        val repo = FakeReportRepository().apply {
            setReports(
                ReportPeriodType.WEEKLY,
                listOf(report(periodType = ReportPeriodType.WEEKLY, periodKey = weekKey, status = ReportStatus.SUCCESS)),
            )
        }
        val viewModel = newViewModel(repo, FakeTransactionRepository())

        assertEquals(ReportPeriodType.WEEKLY, viewModel.uiState.value.periodType)
        assertEquals(listOf(weekKey), viewModel.uiState.value.reports.map { it.periodKey })
        assertFalse(viewModel.uiState.value.loading)
        assertEquals(listOf(ReportPeriodType.WEEKLY), repo.observeCalls)
    }

    @Test
    fun `switches period type and resubscribes`() = runTest {
        val repo = FakeReportRepository().apply {
            setReports(ReportPeriodType.WEEKLY, listOf(report(periodType = ReportPeriodType.WEEKLY, periodKey = "2026-W33")))
            setReports(ReportPeriodType.MONTHLY, listOf(report(periodKey = "2026-07")))
        }
        val viewModel = newViewModel(repo, FakeTransactionRepository())

        viewModel.selectPeriodType(ReportPeriodType.MONTHLY)

        assertEquals(ReportPeriodType.MONTHLY, viewModel.uiState.value.periodType)
        assertEquals(listOf("2026-07"), viewModel.uiState.value.reports.map { it.periodKey })
        assertNull(viewModel.uiState.value.selectedPeriodKey)
    }

    @Test
    fun `open detail loads series and keeps selection`() = runTest {
        val repo = FakeReportRepository().apply {
            setReports(
                ReportPeriodType.WEEKLY,
                listOf(report(periodType = ReportPeriodType.WEEKLY, periodKey = "2026-W34", status = ReportStatus.SUCCESS)),
            )
        }
        val txRepo = FakeTransactionRepository().apply {
            windowTotalsResult = WindowTotals(1_000L, 500L)
        }
        val viewModel = newViewModel(repo, txRepo)

        viewModel.openDetail("2026-W34")
        runCurrent()

        assertEquals("2026-W34", viewModel.uiState.value.selectedPeriodKey)
        assertNotNull(viewModel.uiState.value.series)
        assertFalse(viewModel.uiState.value.seriesLoading)
    }

    @Test
    fun `retry failed monthly report invokes use case with derived windows`() = runTest {
        val repo = FakeReportRepository()
        val txRepo = FakeTransactionRepository()
        val viewModel = newViewModel(repo, txRepo)

        viewModel.retry(report(periodKey = "2026-07"))

        val upserted = repo.upserted.single()
        assertEquals(ReportPeriodType.MONTHLY, upserted.periodType)
        assertEquals("2026-07", upserted.periodKey)
        assertEquals(startOfDay(LocalDate.of(2026, 7, 1)), upserted.windowStartMs)
        assertEquals(startOfDay(LocalDate.of(2026, 8, 1)), upserted.windowEndMs)
        assertEquals(
            listOf(
                startOfDay(LocalDate.of(2026, 7, 1)) to startOfDay(LocalDate.of(2026, 8, 1)),
                startOfDay(LocalDate.of(2026, 6, 1)) to startOfDay(LocalDate.of(2026, 7, 1)),
            ),
            txRepo.windowTotalsCalls.take(2),
        )
    }

    @Test
    fun `retry with invalid period key does not crash nor enter generating`() = runTest {
        val repo = FakeReportRepository()
        val viewModel = newViewModel(repo, FakeTransactionRepository())

        viewModel.retry(report(periodKey = "garbage"))

        assertFalse(viewModel.uiState.value.generating)
        assertTrue(repo.upserted.isEmpty())
    }

    @Test
    fun `retry is ignored while generating`() = runTest {
        val repo = FakeReportRepository()
        val gate = CompletableDeferred<Unit>()
        val txRepo = FakeTransactionRepository().apply { windowTotalsGate = gate }
        val viewModel = newViewModel(repo, txRepo)

        viewModel.retry(report(periodKey = "2026-07"))
        assertTrue(viewModel.uiState.value.generating)
        assertEquals(1, txRepo.windowTotalsCalls.size)

        viewModel.retry(report(periodKey = "2026-07"))
        assertEquals(1, txRepo.windowTotalsCalls.size)

        gate.complete(Unit)
        runCurrent()

        assertFalse(viewModel.uiState.value.generating)
        assertEquals(1, repo.upserted.size)
    }

    @Test
    fun `retry stale report clears analysis text`() = runTest {
        val stale = report(periodKey = "2026-07", status = ReportStatus.STALE).copy(
            id = 7L,
            analysisText = "旧分析",
        )
        val repo = FakeReportRepository().apply {
            setReports(ReportPeriodType.MONTHLY, listOf(stale))
        }
        val viewModel = newViewModel(repo, FakeTransactionRepository())

        viewModel.retry(stale)
        runCurrent()

        assertNull(repo.upserted.single().analysisText)
        assertEquals(ReportStatus.SUCCESS, repo.upserted.single().status)
    }

    @Test
    fun `select category share keeps amount percent and drill tag`() = runTest {
        val viewModel = newViewModel(FakeReportRepository(), FakeTransactionRepository())
        val share = CategoryShare(tagName = "餐饮", cents = 1_500L, percent = 50, tagId = 7L)

        viewModel.selectCategoryShare(share, isOtherBucket = false)

        val detail = viewModel.uiState.value.categoryDetail
        assertEquals("餐饮", detail?.label)
        assertEquals(1_500L, detail?.expenseCents)
        assertEquals(50, detail?.percent)
        assertEquals(7L, detail?.drillTagId)
    }

    @Test
    fun `budget snapshot observed when budget set`() = runTest {
        val snap = MonthlyBudgetSnapshot(
            monthlyBudgetCents = 100_000L,
            spentCents = 10_000L,
            remainingCents = 90_000L,
            remainingDays = 20,
            weeklyQuotaCents = 20_000L,
            spentThisWeekCents = 5_000L,
            weeklyRemainingCents = 15_000L,
        )
        val viewModel = newViewModel(
            FakeReportRepository(),
            FakeTransactionRepository(),
            budgetCents = 100_000L,
            budgetSnapshot = snap,
        )
        runCurrent()
        assertEquals(100_000L, viewModel.uiState.value.budgetCents)
        assertEquals(15_000L, viewModel.uiState.value.budgetSnapshot?.weeklyRemainingCents)
    }

    private fun newViewModel(
        repo: ReportRepository,
        txRepo: TransactionRepository,
        parser: FakeParser = FakeParser(available = false),
        budgetCents: Long = 0L,
        budgetSnapshot: MonthlyBudgetSnapshot = MonthlyBudgetSnapshot(0, 0, 0, 1, 0, 0, 0),
    ): ReportViewModel {
        val budgetRepo = FakeMonthlyBudgetRepository(budgetCents)
        val generate = GenerateReportUseCase(txRepo, repo, budgetRepo)
        val ensure = EnsureReportsUseCase(
            FakeReportRepository(),
            GenerateReportUseCase(FakeTransactionRepository(), FakeReportRepository(), budgetRepo),
        )
        return ReportViewModel(
            reportRepository = repo,
            generateReportUseCase = generate,
            ensureReportsUseCase = ensure,
            loadReportSeriesUseCase = LoadReportSeriesUseCase(txRepo),
            deepReadReportUseCase = DeepReadReportUseCase(parser, repo),
            nlTransactionParser = parser,
            monthlyBudgetRepository = budgetRepo,
            monthlyBudgetEngine = FakeBudgetEngine(budgetSnapshot),
        )
    }

    private fun report(
        periodType: ReportPeriodType = ReportPeriodType.MONTHLY,
        periodKey: String,
        status: ReportStatus = ReportStatus.FAILED,
    ) = Report(
        id = 0L,
        periodType = periodType,
        periodKey = periodKey,
        windowStartMs = 0L,
        windowEndMs = 0L,
        incomeCents = 1_000L,
        expenseCents = 500L,
        topCategories = emptyList(),
        prevIncomeCents = 0L,
        prevExpenseCents = 0L,
        analysisText = null,
        localInsights = emptyList(),
        status = status,
        generatedAtMs = 0L,
    )

    private fun startOfDay(date: LocalDate): Long =
        date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

    private class FakeParser(
        private val available: Boolean,
        private val reply: String? = null,
    ) : NLTransactionParser {
        override suspend fun isAvailable(): Boolean = available
        override suspend fun generate(systemInstruction: String, userText: String): String? = reply
    }

    private class FakeMonthlyBudgetRepository(
        initial: Long = 0L,
    ) : MonthlyBudgetRepository {
        private val flow = MutableStateFlow(initial)
        override fun observeMonthlyBudgetCents(): Flow<Long> = flow
        override suspend fun saveMonthlyBudgetCents(cents: Long) {
            flow.value = cents
        }
    }

    private class FakeBudgetEngine(
        private val snapshot: MonthlyBudgetSnapshot,
    ) : MonthlyBudgetEngine {
        override fun observeSnapshot(today: LocalDate): Flow<MonthlyBudgetSnapshot> = flowOf(snapshot)
    }

    private class FakeReportRepository : ReportRepository {
        val observeCalls = mutableListOf<ReportPeriodType>()
        val upserted = mutableListOf<Report>()
        private val flows = mutableMapOf<ReportPeriodType, MutableStateFlow<List<Report>>>()

        fun setReports(type: ReportPeriodType, reports: List<Report>) {
            flows.getOrPut(type) { MutableStateFlow(emptyList()) }.value = reports
        }

        override fun observeByType(type: ReportPeriodType): Flow<List<Report>> {
            observeCalls += type
            return flows.getOrPut(type) { MutableStateFlow(emptyList()) }
        }

        override suspend fun getByKey(periodType: ReportPeriodType, periodKey: String): Report? =
            flows[periodType]?.value?.firstOrNull { it.periodKey == periodKey }
                ?: upserted.lastOrNull { it.periodType == periodType && it.periodKey == periodKey }

        override suspend fun upsert(report: Report) {
            upserted += report
            val flow = flows.getOrPut(report.periodType) { MutableStateFlow(emptyList()) }
            val without = flow.value.filterNot {
                it.periodType == report.periodType && it.periodKey == report.periodKey
            }
            flow.value = without + report
        }

        override suspend fun invalidateWhereWindowContains(epochMillis: Long) = Unit
    }

    private class FakeTransactionRepository : TransactionRepository {
        val windowTotalsCalls = mutableListOf<Pair<Long, Long>>()
        var windowTotalsResult: WindowTotals = WindowTotals(0L, 0L)
        var windowTotalsGate: CompletableDeferred<Unit>? = null

        override suspend fun add(transaction: Transaction): Long = 0L
        override suspend fun delete(transactionId: Long) = Unit
        override fun observeAll(): Flow<List<Transaction>> = flowOf(emptyList())
        override fun observeByTag(tagId: Long): Flow<List<Transaction>> = flowOf(emptyList())
        override fun observeExpenseSumBetween(startInclusiveMs: Long, endExclusiveMs: Long): Flow<Long> = flowOf(0L)
        override fun observeRecent(limit: Int): Flow<List<RecentTransaction>> = flowOf(emptyList())
        override fun observeFiltered(
            startInclusiveMs: Long?,
            endExclusiveMs: Long?,
            tagIds: List<Long>,
            noteKeyword: String?,
            accountFilter: AccountFilter,
        ): Flow<List<RecentTransaction>> = flowOf(emptyList())
        override fun observeUncategorizedCount(): Flow<Int> = flowOf(0)
        override suspend fun getUncategorized(): List<RecentTransaction> = emptyList()
        override suspend fun assignTags(assignments: Map<Long, List<Long>>) = Unit
        override suspend fun getOccurredAtsByTagIds(tagIds: List<Long>): List<Long> = emptyList()
        override suspend fun getById(id: Long): Transaction? = null
        override suspend fun updateTransaction(transaction: Transaction) = Unit
        override fun observeHeldCents(): Flow<Long> = flowOf(0L)
        override suspend fun getWindowTotals(startInclusiveMs: Long, endExclusiveMs: Long): WindowTotals {
            windowTotalsCalls += startInclusiveMs to endExclusiveMs
            windowTotalsGate?.await()
            return windowTotalsResult
        }
        override suspend fun getExpenseByCategory(startInclusiveMs: Long, endExclusiveMs: Long): List<CategoryExpense> =
            emptyList()
        override suspend fun countUncategorizedBetween(startInclusiveMs: Long, endExclusiveMs: Long): Int = 0
        override suspend fun getMaxExpenseCentsBetween(startInclusiveMs: Long, endExclusiveMs: Long): Long? = null
        override suspend fun getBetween(startInclusiveMs: Long, endExclusiveMs: Long): List<RecentTransaction> =
            emptyList()
    }
}
