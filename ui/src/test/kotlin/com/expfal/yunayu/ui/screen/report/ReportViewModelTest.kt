package com.expfal.yunayu.ui.screen.report

import com.expfal.yunayu.domain.model.CategoryExpense
import com.expfal.yunayu.domain.model.RecentTransaction
import com.expfal.yunayu.domain.model.Transaction
import com.expfal.yunayu.domain.model.WindowTotals
import com.expfal.yunayu.domain.report.GenerateReportUseCase
import com.expfal.yunayu.domain.report.ReportAnalyzer
import com.expfal.yunayu.domain.report.model.Report
import com.expfal.yunayu.domain.report.model.ReportPeriodType
import com.expfal.yunayu.domain.report.model.ReportStatus
import com.expfal.yunayu.domain.repository.ReportRepository
import com.expfal.yunayu.domain.repository.TransactionRepository
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
    fun `observes monthly reports by default`() = runTest {
        val repo = FakeReportRepository().apply {
            setReports(ReportPeriodType.MONTHLY, listOf(report(periodKey = "2026-07", status = ReportStatus.SUCCESS)))
        }
        val viewModel = newViewModel(repo, FakeTransactionRepository())

        assertEquals(ReportPeriodType.MONTHLY, viewModel.uiState.value.periodType)
        assertEquals(listOf("2026-07"), viewModel.uiState.value.reports.map { it.periodKey })
        assertFalse(viewModel.uiState.value.loading)
        assertEquals(listOf(ReportPeriodType.MONTHLY), repo.observeCalls)
    }

    @Test
    fun `switches period type and resubscribes`() = runTest {
        val repo = FakeReportRepository().apply {
            setReports(ReportPeriodType.MONTHLY, listOf(report(periodKey = "2026-07")))
            setReports(ReportPeriodType.ANNUAL, listOf(report(periodType = ReportPeriodType.ANNUAL, periodKey = "2026")))
        }
        val viewModel = newViewModel(repo, FakeTransactionRepository())

        viewModel.selectPeriodType(ReportPeriodType.ANNUAL)

        assertEquals(ReportPeriodType.ANNUAL, viewModel.uiState.value.periodType)
        assertEquals(listOf("2026"), viewModel.uiState.value.reports.map { it.periodKey })
        assertEquals(listOf(ReportPeriodType.MONTHLY, ReportPeriodType.ANNUAL), repo.observeCalls)
        assertNull(viewModel.uiState.value.selectedPeriodKey)
    }

    @Test
    fun `empty list state when no reports`() = runTest {
        val viewModel = newViewModel(FakeReportRepository(), FakeTransactionRepository())

        assertTrue(viewModel.uiState.value.reports.isEmpty())
        assertFalse(viewModel.uiState.value.loading)
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
            txRepo.windowTotalsCalls,
        )
    }

    @Test
    fun `retry failed annual report uses year windows`() = runTest {
        val repo = FakeReportRepository()
        val txRepo = FakeTransactionRepository()
        val viewModel = newViewModel(repo, txRepo)

        viewModel.retry(report(periodType = ReportPeriodType.ANNUAL, periodKey = "2026"))

        val upserted = repo.upserted.single()
        assertEquals(ReportPeriodType.ANNUAL, upserted.periodType)
        assertEquals("2026", upserted.periodKey)
        assertEquals(startOfDay(LocalDate.of(2026, 1, 1)), upserted.windowStartMs)
        assertEquals(startOfDay(LocalDate.of(2027, 1, 1)), upserted.windowEndMs)
        assertEquals(
            listOf(
                startOfDay(LocalDate.of(2026, 1, 1)) to startOfDay(LocalDate.of(2027, 1, 1)),
                startOfDay(LocalDate.of(2025, 1, 1)) to startOfDay(LocalDate.of(2026, 1, 1)),
            ),
            txRepo.windowTotalsCalls,
        )
    }

    @Test
    fun `retry with invalid period key does not crash nor enter generating`() = runTest {
        val repo = FakeReportRepository()
        val txRepo = FakeTransactionRepository()
        val analyzer = FakeReportAnalyzer().apply { available = true }
        val viewModel = newViewModel(repo, txRepo, analyzer)

        viewModel.retry(report(periodKey = "garbage"))

        assertFalse(viewModel.uiState.value.generating)
        assertTrue(repo.upserted.isEmpty())
        assertEquals(0, analyzer.analyzeCalls.size)
    }

    @Test
    fun `retry is ignored while generating`() = runTest {
        val repo = FakeReportRepository()
        val txRepo = FakeTransactionRepository()
        val gate = CompletableDeferred<Unit>()
        val analyzer = FakeReportAnalyzer().apply {
            available = true
            analyzeGate = gate
        }
        val viewModel = newViewModel(repo, txRepo, analyzer)

        viewModel.retry(report(periodKey = "2026-07"))
        assertTrue(viewModel.uiState.value.generating)
        assertEquals(1, analyzer.analyzeCalls.size)

        viewModel.retry(report(periodKey = "2026-07"))
        assertEquals(1, analyzer.analyzeCalls.size)

        gate.complete(Unit)
        runCurrent()

        assertFalse(viewModel.uiState.value.generating)
        assertEquals(1, repo.upserted.size)
    }

    private fun newViewModel(
        repo: ReportRepository,
        txRepo: TransactionRepository,
        analyzer: ReportAnalyzer = FakeReportAnalyzer(),
    ) = ReportViewModel(
        reportRepository = repo,
        generateReportUseCase = GenerateReportUseCase(txRepo, repo, analyzer),
    )

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
        status = status,
        generatedAtMs = 0L,
    )

    private fun startOfDay(date: LocalDate): Long =
        date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

    /** [ReportRepository] 手写 fake：按类型维护 StateFlow，记录 observe/upsert 调用。 */
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

        override suspend fun getByKey(periodType: ReportPeriodType, periodKey: String): Report? = null

        override suspend fun upsert(report: Report) {
            upserted += report
            val flow = flows.getOrPut(report.periodType) { MutableStateFlow(emptyList()) }
            flow.value = flow.value + report
        }

        override suspend fun invalidateWhereWindowContains(epochMillis: Long) = Unit
    }

    /** [TransactionRepository] 手写 fake：记录窗口聚合调用，返回空汇总。 */
    private class FakeTransactionRepository : TransactionRepository {

        val windowTotalsCalls = mutableListOf<Pair<Long, Long>>()
        var windowTotalsResult: WindowTotals = WindowTotals(0L, 0L)

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
        ): Flow<List<RecentTransaction>> = flowOf(emptyList())

        override fun observeHeldCents(): Flow<Long> = flowOf(0L)

        override suspend fun getWindowTotals(
            startInclusiveMs: Long,
            endExclusiveMs: Long,
        ): WindowTotals {
            windowTotalsCalls += startInclusiveMs to endExclusiveMs
            return windowTotalsResult
        }

        override suspend fun getExpenseByCategory(
            startInclusiveMs: Long,
            endExclusiveMs: Long,
        ): List<CategoryExpense> = emptyList()
    }

    /** [ReportAnalyzer] 手写 fake：可控可用性与挂起门，供重试路径与防重入测试。 */
    private class FakeReportAnalyzer : ReportAnalyzer {

        var available: Boolean = false
        var analyzeGate: CompletableDeferred<Unit>? = null
        val analyzeCalls = mutableListOf<Pair<String, String>>()

        override suspend fun isAvailable(): Boolean = available

        override suspend fun analyze(systemInstruction: String, dataText: String): String? {
            analyzeCalls += systemInstruction to dataText
            analyzeGate?.await()
            return null
        }
    }
}
