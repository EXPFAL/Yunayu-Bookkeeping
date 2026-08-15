package com.expfal.yunayu.domain.report

import com.expfal.yunayu.domain.model.CategoryExpense
import com.expfal.yunayu.domain.model.RecentTransaction
import com.expfal.yunayu.domain.model.Transaction
import com.expfal.yunayu.domain.model.WindowTotals
import com.expfal.yunayu.domain.report.model.Report
import com.expfal.yunayu.domain.report.model.ReportPeriodType
import com.expfal.yunayu.domain.report.model.ReportStatus
import com.expfal.yunayu.domain.repository.ReportRepository
import com.expfal.yunayu.domain.repository.TransactionRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
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
                CategoryExpense("餐饮", 1_500L),
                CategoryExpense(null, 500L),
            ),
        )
        val reportRepository = FakeReportRepository()
        val analyzer = FakeReportAnalyzer(available = true).apply { analyzeResult = "消费分析结论" }
        val useCase = GenerateReportUseCase(transactionRepository, reportRepository, analyzer)

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
        assertEquals(listOf(100L to 200L, 0L to 100L), transactionRepository.windowTotalsCalls)
    }

    @Test
    fun `analyzer returning null persists failed report`() = runTest {
        val reportRepository = FakeReportRepository()
        val analyzer = FakeReportAnalyzer(available = true).apply { analyzeResult = null }
        val useCase = GenerateReportUseCase(FakeTransactionRepository(), reportRepository, analyzer)

        useCase(MONTHLY, "2026-07", 0L, 100L, 0L, 100L)

        val report = reportRepository.upserted.single()
        assertEquals(ReportStatus.FAILED, report.status)
        assertNull(report.analysisText)
    }

    @Test
    fun `unavailable analyzer persists failed report without calling analyze`() = runTest {
        val reportRepository = FakeReportRepository()
        val analyzer = FakeReportAnalyzer(available = false)
        val useCase = GenerateReportUseCase(FakeTransactionRepository(), reportRepository, analyzer)

        useCase(MONTHLY, "2026-07", 0L, 100L, 0L, 100L)

        assertEquals(ReportStatus.FAILED, reportRepository.upserted.single().status)
        assertEquals(0, analyzer.analyzeCalls.size)
    }

    @Test
    fun `blank analysis yields success report with null text`() = runTest {
        val reportRepository = FakeReportRepository()
        val analyzer = FakeReportAnalyzer(available = true).apply { analyzeResult = "   " }
        val useCase = GenerateReportUseCase(FakeTransactionRepository(), reportRepository, analyzer)

        useCase(MONTHLY, "2026-07", 0L, 100L, 0L, 100L)

        val report = reportRepository.upserted.single()
        assertEquals(ReportStatus.SUCCESS, report.status)
        assertNull(report.analysisText)
    }

    @Test
    fun `timeout persists failed report`() = runTest {
        val reportRepository = FakeReportRepository()
        val analyzer = FakeReportAnalyzer(available = true).apply { blockAnalysis = true }
        val useCase = GenerateReportUseCase(FakeTransactionRepository(), reportRepository, analyzer)

        val job = launch { useCase(MONTHLY, "2026-07", 0L, 100L, 0L, 100L) }
        advanceTimeBy(66_000)
        job.join()

        assertEquals(ReportStatus.FAILED, reportRepository.upserted.single().status)
    }

    @Test
    fun `overlong analysis text is truncated to max chars`() = runTest {
        val reportRepository = FakeReportRepository()
        val analyzer = FakeReportAnalyzer(available = true).apply { analyzeResult = "析".repeat(3000) }
        val useCase = GenerateReportUseCase(FakeTransactionRepository(), reportRepository, analyzer)

        useCase(MONTHLY, "2026-07", 0L, 100L, 0L, 100L)

        val report = reportRepository.upserted.single()
        assertEquals(ReportStatus.SUCCESS, report.status)
        assertEquals(2000, report.analysisText?.length)
    }

    @Test
    fun `analysis text with markdown code fence is stripped`() = runTest {
        val reportRepository = FakeReportRepository()
        val analyzer = FakeReportAnalyzer(available = true).apply { analyzeResult = "```text\n结论\n```" }
        val useCase = GenerateReportUseCase(FakeTransactionRepository(), reportRepository, analyzer)

        useCase(MONTHLY, "2026-07", 0L, 100L, 0L, 100L)

        assertEquals("结论", reportRepository.upserted.single().analysisText)
    }

    @Test
    fun `truncation does not split a surrogate pair`() = runTest {
        val reportRepository = FakeReportRepository()
        val analyzer = FakeReportAnalyzer(available = true).apply {
            analyzeResult = "a" + "😀".repeat(1000)
        }
        val useCase = GenerateReportUseCase(FakeTransactionRepository(), reportRepository, analyzer)

        useCase(MONTHLY, "2026-07", 0L, 100L, 0L, 100L)

        val text = reportRepository.upserted.single().analysisText!!
        assertEquals(1999, text.length)
        assertFalse(Character.isHighSurrogate(text.last()))
    }

    private companion object {
        val MONTHLY = ReportPeriodType.MONTHLY
    }

    /** [TransactionRepository] 手写 fake：按调用顺序返回当期 / 上期收支。 */
    private class FakeTransactionRepository(
        private val currentTotals: WindowTotals = WindowTotals(0L, 0L),
        private val prevTotals: WindowTotals = WindowTotals(0L, 0L),
        private val categoryExpenses: List<CategoryExpense> = emptyList(),
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
        ): Flow<List<RecentTransaction>> = flowOf(emptyList())

        override suspend fun getWindowTotals(
            startInclusiveMs: Long,
            endExclusiveMs: Long,
        ): WindowTotals {
            windowTotalsCalls += startInclusiveMs to endExclusiveMs
            return if (windowTotalsCalls.size == 1) currentTotals else prevTotals
        }

        override suspend fun getExpenseByCategory(
            startInclusiveMs: Long,
            endExclusiveMs: Long,
        ): List<CategoryExpense> {
            categoryCalls += startInclusiveMs to endExclusiveMs
            return categoryExpenses
        }
    }

    /** [ReportRepository] 手写 fake：记录 upsert 入参。 */
    private class FakeReportRepository : ReportRepository {
        val upserted = mutableListOf<Report>()

        override fun observeByType(type: ReportPeriodType): Flow<List<Report>> = flowOf(emptyList())

        override suspend fun getByKey(periodType: ReportPeriodType, periodKey: String): Report? = null

        override suspend fun upsert(report: Report) {
            upserted += report
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
