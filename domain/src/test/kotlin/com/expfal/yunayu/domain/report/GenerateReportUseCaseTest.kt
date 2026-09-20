package com.expfal.yunayu.domain.report

import com.expfal.yunayu.domain.model.AccountFilter
import com.expfal.yunayu.domain.model.CategoryExpense
import com.expfal.yunayu.domain.model.CategoryNoteSample
import com.expfal.yunayu.domain.model.RecentTransaction
import com.expfal.yunayu.domain.model.Transaction
import com.expfal.yunayu.domain.model.WindowTotals
import com.expfal.yunayu.domain.report.model.Report
import com.expfal.yunayu.domain.report.model.ReportPeriodType
import com.expfal.yunayu.domain.report.model.ReportStatus
import com.expfal.yunayu.domain.repository.MonthlyBudgetRepository
import com.expfal.yunayu.domain.repository.ReportRepository
import com.expfal.yunayu.domain.repository.TransactionRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** [GenerateReportUseCase] 的 JVM 单元测试（手写 fake 仓储 + 分析器）。 */
@OptIn(ExperimentalCoroutinesApi::class)
class GenerateReportUseCaseTest {

    private val currentTotals = WindowTotals(incomeCents = 5_000L, expenseCents = 3_000L)
    private val prevTotals = WindowTotals(incomeCents = 4_000L, expenseCents = 2_500L)

    @Test
    fun `successful analysis persists success report with structured data`() = runTest {
        val transactionRepository = FakeTransactionRepository(
            currentTotals = currentTotals,
            prevTotals = prevTotals,
            categoryExpenses = listOf(
                CategoryExpense("餐饮", 1_500L, tagId = 1L),
                CategoryExpense(null, 500L),
            ),
        )
        val reportRepository = FakeReportRepository()
        val analyzer = FakeReportAnalyzer(available = true).apply { analyzeResult = "消费分析结论" }
        val useCase = newUseCase(transactionRepository, reportRepository, analyzer)

        useCase(MONTHLY, "2026-07", 100L, 200L, 0L, 100L)

        val report = reportRepository.upserted.single()
        assertEquals(ReportStatus.SUCCESS, report.status)
        assertEquals("消费分析结论", report.analysisText)
        assertEquals(5_000L, report.incomeCents)
        assertEquals(3_000L, report.expenseCents)
        assertEquals(4_000L, report.prevIncomeCents)
        assertEquals(2_500L, report.prevExpenseCents)
        assertEquals(listOf("餐饮", null), report.topCategories.map { it.tagName })
        assertEquals(listOf(50, 16), report.topCategories.map { it.percent })
        assertTrue(report.localInsights.isNotEmpty())
        assertTrue(transactionRepository.windowTotalsCalls.size >= 2)
    }

    @Test
    fun `analyzer returning null still persists success with insights`() = runTest {
        val reportRepository = FakeReportRepository()
        val analyzer = FakeReportAnalyzer(available = true).apply { analyzeResult = null }
        val useCase = newUseCase(FakeTransactionRepository(), reportRepository, analyzer)

        useCase(MONTHLY, "2026-07", 0L, 100L, 0L, 100L)

        val report = reportRepository.upserted.single()
        assertEquals(ReportStatus.SUCCESS, report.status)
        assertNull(report.analysisText)
        assertTrue(report.localInsights.isNotEmpty())
    }

    @Test
    fun `unavailable analyzer persists success with insights without calling analyze`() = runTest {
        val reportRepository = FakeReportRepository()
        val analyzer = FakeReportAnalyzer(available = false)
        val useCase = newUseCase(FakeTransactionRepository(), reportRepository, analyzer)

        useCase(MONTHLY, "2026-07", 0L, 100L, 0L, 100L)

        val report = reportRepository.upserted.single()
        assertEquals(ReportStatus.SUCCESS, report.status)
        assertNull(report.analysisText)
        assertTrue(report.localInsights.isNotEmpty())
        assertEquals(0, analyzer.analyzeCalls.size)
    }

    @Test
    fun `blank analysis yields success report with null text`() = runTest {
        val reportRepository = FakeReportRepository()
        val analyzer = FakeReportAnalyzer(available = true).apply { analyzeResult = "   " }
        val useCase = newUseCase(FakeTransactionRepository(), reportRepository, analyzer)

        useCase(MONTHLY, "2026-07", 0L, 100L, 0L, 100L)

        val report = reportRepository.upserted.single()
        assertEquals(ReportStatus.SUCCESS, report.status)
        assertNull(report.analysisText)
    }

    @Test
    fun `timeout still persists success with insights`() = runTest {
        val reportRepository = FakeReportRepository()
        val analyzer = FakeReportAnalyzer(available = true).apply { blockAnalysis = true }
        val useCase = newUseCase(FakeTransactionRepository(), reportRepository, analyzer)

        val job = launch { useCase(MONTHLY, "2026-07", 0L, 100L, 0L, 100L) }
        advanceTimeBy(66_000)
        job.join()

        val report = reportRepository.upserted.single()
        assertEquals(ReportStatus.SUCCESS, report.status)
        assertNull(report.analysisText)
        assertTrue(report.localInsights.isNotEmpty())
    }

    @Test
    fun `analyze data text includes sampled category notes`() = runTest {
        val transactionRepository = FakeTransactionRepository(
            currentTotals = currentTotals,
            prevTotals = prevTotals,
            categoryExpenses = listOf(CategoryExpense("餐饮", 1_500L, tagId = 1L)),
            noteSamples = listOf(
                CategoryNoteSample(1L, "餐饮", listOf("食堂套餐")),
            ),
        )
        val analyzer = FakeReportAnalyzer(available = true).apply { analyzeResult = "结合备注的点评" }
        val useCase = newUseCase(transactionRepository, FakeReportRepository(), analyzer)

        useCase(MONTHLY, "2026-07", 100L, 200L, 0L, 100L)

        val dataText = analyzer.analyzeCalls.single().second
        assertTrue(dataText.contains("各类代表备注"))
        assertTrue(dataText.contains("食堂套餐"))
    }

    @Test
    fun `overlong analysis text is truncated to max chars`() = runTest {
        val reportRepository = FakeReportRepository()
        val analyzer = FakeReportAnalyzer(available = true).apply { analyzeResult = "析".repeat(3000) }
        val useCase = newUseCase(FakeTransactionRepository(), reportRepository, analyzer)

        useCase(MONTHLY, "2026-07", 0L, 100L, 0L, 100L)

        val report = reportRepository.upserted.single()
        assertEquals(ReportStatus.SUCCESS, report.status)
        assertEquals(2000, report.analysisText?.length)
    }

    @Test
    fun `analysis text with markdown code fence is stripped`() = runTest {
        val reportRepository = FakeReportRepository()
        val analyzer = FakeReportAnalyzer(available = true).apply { analyzeResult = "```text\n结论\n```" }
        val useCase = newUseCase(FakeTransactionRepository(), reportRepository, analyzer)

        useCase(MONTHLY, "2026-07", 0L, 100L, 0L, 100L)

        assertEquals("结论", reportRepository.upserted.single().analysisText)
    }

    @Test
    fun `truncation does not split a surrogate pair`() = runTest {
        val reportRepository = FakeReportRepository()
        val analyzer = FakeReportAnalyzer(available = true).apply {
            analyzeResult = "a" + "😀".repeat(1000)
        }
        val useCase = newUseCase(FakeTransactionRepository(), reportRepository, analyzer)

        useCase(MONTHLY, "2026-07", 0L, 100L, 0L, 100L)

        val text = reportRepository.upserted.single().analysisText!!
        assertEquals(1999, text.length)
        assertFalse(Character.isHighSurrogate(text.last()))
    }

    @Test
    fun `regenerate reuses existing report id`() = runTest {
        val existing = Report(
            id = 42L,
            periodType = MONTHLY,
            periodKey = "2026-07",
            windowStartMs = 100L,
            windowEndMs = 200L,
            incomeCents = 1L,
            expenseCents = 1L,
            topCategories = emptyList(),
            prevIncomeCents = 0L,
            prevExpenseCents = 0L,
            analysisText = "旧分析",
            localInsights = emptyList(),
            status = ReportStatus.STALE,
            generatedAtMs = 1L,
        )
        val reportRepository = FakeReportRepository().apply { seed(existing) }
        val analyzer = FakeReportAnalyzer(available = true).apply { analyzeResult = "新分析" }
        val useCase = newUseCase(FakeTransactionRepository(), reportRepository, analyzer)

        useCase(MONTHLY, "2026-07", 100L, 200L, 0L, 100L)

        val report = reportRepository.upserted.single()
        assertEquals(42L, report.id)
        assertEquals(ReportStatus.SUCCESS, report.status)
        assertEquals("新分析", report.analysisText)
    }

    private fun newUseCase(
        tx: TransactionRepository,
        reports: ReportRepository,
        analyzer: ReportAnalyzer,
    ) = GenerateReportUseCase(tx, reports, analyzer, FakeMonthlyBudgetRepository())

    private companion object {
        val MONTHLY = ReportPeriodType.MONTHLY
    }

    private class FakeMonthlyBudgetRepository(
        private val budgetFlow: MutableStateFlow<Long> = MutableStateFlow(0L),
    ) : MonthlyBudgetRepository {
        override fun observeMonthlyBudgetCents(): Flow<Long> = budgetFlow
        override suspend fun saveMonthlyBudgetCents(cents: Long) {
            budgetFlow.value = cents
        }
    }

    /** [TransactionRepository] 手写 fake：按调用顺序返回当期 / 上期收支。 */
    private class FakeTransactionRepository(
        private val currentTotals: WindowTotals = WindowTotals(0L, 0L),
        private val prevTotals: WindowTotals = WindowTotals(0L, 0L),
        private val categoryExpenses: List<CategoryExpense> = emptyList(),
        private val noteSamples: List<CategoryNoteSample> = emptyList(),
    ) : TransactionRepository {

        val windowTotalsCalls = mutableListOf<Pair<Long, Long>>()
        val categoryCalls = mutableListOf<Pair<Long, Long>>()

        override suspend fun add(transaction: Transaction): Long = 0L

        override suspend fun delete(transactionId: Long) = Unit

        override fun observeAll(): Flow<List<Transaction>> = flowOf(emptyList())

        override fun observeByTag(tagId: Long): Flow<List<Transaction>> = flowOf(emptyList())

        override fun observeExpenseSumBetween(startInclusiveMs: Long, endExclusiveMs: Long): Flow<Long> =
            flowOf(0L)

        override fun observeHeldCents(): Flow<Long> = flowOf(0L)

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

        override suspend fun getWindowTotals(
            startInclusiveMs: Long,
            endExclusiveMs: Long,
        ): WindowTotals {
            windowTotalsCalls += startInclusiveMs to endExclusiveMs
            return when (windowTotalsCalls.size) {
                1 -> currentTotals
                2 -> prevTotals
                else -> WindowTotals(0L, 0L)
            }
        }

        override suspend fun getExpenseByCategory(
            startInclusiveMs: Long,
            endExclusiveMs: Long,
        ): List<CategoryExpense> {
            categoryCalls += startInclusiveMs to endExclusiveMs
            return categoryExpenses
        }

        override suspend fun countUncategorizedBetween(startInclusiveMs: Long, endExclusiveMs: Long): Int = 0

        override suspend fun getMaxExpenseCentsBetween(startInclusiveMs: Long, endExclusiveMs: Long): Long? = null

        override suspend fun getExpenseNotesByCategory(
            startInclusiveMs: Long,
            endExclusiveMs: Long,
            limitPerCategory: Int,
        ): List<CategoryNoteSample> = noteSamples
    }

    /** [ReportRepository] 手写 fake：按 period 键存取，记录 upsert 入参。 */
    private class FakeReportRepository : ReportRepository {
        val upserted = mutableListOf<Report>()
        private val byKey = mutableMapOf<Pair<ReportPeriodType, String>, Report>()

        fun seed(report: Report) {
            byKey[report.periodType to report.periodKey] = report
        }

        override fun observeByType(type: ReportPeriodType): Flow<List<Report>> = flowOf(emptyList())

        override suspend fun getByKey(periodType: ReportPeriodType, periodKey: String): Report? =
            byKey[periodType to periodKey]

        override suspend fun upsert(report: Report) {
            upserted += report
            byKey[report.periodType to report.periodKey] = report
        }

        override suspend fun invalidateWhereWindowContains(epochMillis: Long) = Unit
    }

    /** [ReportAnalyzer] 手写 fake：可控可用性 / 返回 / 永久挂起（测超时）。 */
    private class FakeReportAnalyzer(
        var available: Boolean = true,
    ) : ReportAnalyzer {
        var analyzeResult: String? = "分析文本"
        var blockAnalysis: Boolean = false
        val analyzeCalls = mutableListOf<Pair<String, String>>()

        override suspend fun isAvailable(): Boolean = available

        override suspend fun analyze(systemInstruction: String, dataText: String): String? {
            analyzeCalls += systemInstruction to dataText
            if (blockAnalysis) awaitCancellation()
            return analyzeResult
        }
    }
}
