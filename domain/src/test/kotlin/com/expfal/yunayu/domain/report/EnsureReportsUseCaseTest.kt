package com.expfal.yunayu.domain.report

import com.expfal.yunayu.domain.model.AccountFilter
import com.expfal.yunayu.domain.model.CategoryExpense
import com.expfal.yunayu.domain.model.RecentTransaction
import com.expfal.yunayu.domain.model.Transaction
import com.expfal.yunayu.domain.model.WindowTotals
import com.expfal.yunayu.domain.report.model.Report
import com.expfal.yunayu.domain.report.model.ReportPeriodType
import com.expfal.yunayu.domain.report.model.ReportStatus
import com.expfal.yunayu.domain.repository.MonthlyBudgetRepository
import com.expfal.yunayu.domain.repository.ReportRepository
import com.expfal.yunayu.domain.repository.TransactionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate

/** [EnsureReportsUseCase] 的 JVM 单元测试（真实 [GenerateReportUseCase] + 手写 fake 仓储/分析器）。 */
class EnsureReportsUseCaseTest {

    @Test
    fun `ensures previous and current month and week when missing`() = runTest {
        val reportRepository = FakeReportRepository()
        val useCase = newUseCase(reportRepository)

        useCase.ensure(LocalDate.of(2026, 8, 15))

        val upserted = reportRepository.upserted
        assertEquals(4, upserted.size) // 上周 + 上月 + 本周 + 本月
        assertEquals(
            setOf(
                ReportPeriodType.WEEKLY to "2026-W32",
                ReportPeriodType.WEEKLY to "2026-W33",
                ReportPeriodType.MONTHLY to "2026-07",
                ReportPeriodType.MONTHLY to "2026-08",
            ),
            upserted.map { it.periodType to it.periodKey }.toSet(),
        )
    }

    @Test
    fun `ensures annual report only in january plus current periods`() = runTest {
        val reportRepository = FakeReportRepository()
        val useCase = newUseCase(reportRepository)

        useCase.ensure(LocalDate.of(2026, 1, 15))

        val upserted = reportRepository.upserted
        assertEquals(5, upserted.size) // 上周 + 上月 + 年报 + 本周 + 本月
        assertEquals(
            setOf(
                ReportPeriodType.WEEKLY to "2026-W02",
                ReportPeriodType.WEEKLY to "2026-W03",
                ReportPeriodType.MONTHLY to "2025-12",
                ReportPeriodType.MONTHLY to "2026-01",
                ReportPeriodType.ANNUAL to "2025",
            ),
            upserted.map { it.periodType to it.periodKey }.toSet(),
        )
    }

    @Test
    fun `skips existing report including failed`() = runTest {
        val reportRepository = FakeReportRepository().apply {
            existing[ReportPeriodType.MONTHLY to "2026-07"] =
                report(ReportPeriodType.MONTHLY, "2026-07", ReportStatus.FAILED)
        }
        val useCase = newUseCase(reportRepository)

        useCase.ensure(LocalDate.of(2026, 8, 15))

        // 上周 + 本周 + 本月（上月已存在）
        assertEquals(3, reportRepository.upserted.size)
        assertTrue(reportRepository.upserted.none { it.periodKey == "2026-07" })
    }

    @Test
    fun `skips annual outside january`() = runTest {
        val reportRepository = FakeReportRepository()
        val useCase = newUseCase(reportRepository)

        useCase.ensure(LocalDate.of(2026, 12, 15))

        assertEquals(4, reportRepository.upserted.size) // 上周 + 上月 + 本周 + 本月
        assertTrue(reportRepository.upserted.none { it.periodType == ReportPeriodType.ANNUAL })
        val monthly = reportRepository.upserted.filter { it.periodType == ReportPeriodType.MONTHLY }
            .map { it.periodKey }.toSet()
        assertEquals(setOf("2026-11", "2026-12"), monthly)
    }

    @Test
    fun `ensures previous week report when missing`() = runTest {
        val reportRepository = FakeReportRepository()
        val useCase = newUseCase(reportRepository)

        // 2026-08-17 是周一：上周 W33、本周 W34、上月 07、本月 08
        useCase.ensure(LocalDate.of(2026, 8, 17))

        val upserted = reportRepository.upserted
        assertEquals(4, upserted.size)
        val weekly = upserted.filter { it.periodType == ReportPeriodType.WEEKLY }.map { it.periodKey }.toSet()
        assertEquals(setOf("2026-W33", "2026-W34"), weekly)
    }

    private fun newUseCase(reportRepository: FakeReportRepository) = EnsureReportsUseCase(
        reportRepository,
        GenerateReportUseCase(
            FakeTransactionRepository(),
            reportRepository,
            FakeReportAnalyzer(),
            FakeMonthlyBudgetRepository(),
        ),
    )

    private class FakeMonthlyBudgetRepository : MonthlyBudgetRepository {
        override fun observeMonthlyBudgetCents(): Flow<Long> = MutableStateFlow(0L)
        override suspend fun saveMonthlyBudgetCents(cents: Long) = Unit
    }

    /** [TransactionRepository] 手写 fake：返回空聚合（报告结构化数据非本测试关注点）。 */
    private class FakeTransactionRepository : TransactionRepository {

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
        ): WindowTotals = WindowTotals(0L, 0L)

        override suspend fun getExpenseByCategory(
            startInclusiveMs: Long,
            endExclusiveMs: Long,
        ): List<CategoryExpense> = emptyList()
    
        override suspend fun countUncategorizedBetween(startInclusiveMs: Long, endExclusiveMs: Long): Int = 0

        override suspend fun getMaxExpenseCentsBetween(startInclusiveMs: Long, endExclusiveMs: Long): Long? = null
}

    /** [ReportRepository] 手写 fake：按周期键返回预置报告，记录 upsert。 */
    private class FakeReportRepository : ReportRepository {
        val existing = mutableMapOf<Pair<ReportPeriodType, String>, Report>()
        val upserted = mutableListOf<Report>()

        override fun observeByType(type: ReportPeriodType): Flow<List<Report>> = flowOf(emptyList())

        override suspend fun getByKey(periodType: ReportPeriodType, periodKey: String): Report? =
            existing[periodType to periodKey]

        override suspend fun upsert(report: Report) {
            upserted += report
            existing[report.periodType to report.periodKey] = report
        }

        override suspend fun invalidateWhereWindowContains(epochMillis: Long) = Unit
    }

    /** [ReportAnalyzer] 手写 fake：始终可用并返回固定分析文本。 */
    private class FakeReportAnalyzer : ReportAnalyzer {
        override suspend fun isAvailable(): Boolean = true

        override suspend fun analyze(systemInstruction: String, dataText: String): String = "分析结论"
    }

    private fun report(
        periodType: ReportPeriodType,
        periodKey: String,
        status: ReportStatus,
    ): Report = Report(
        periodType = periodType,
        periodKey = periodKey,
        windowStartMs = 0L,
        windowEndMs = 1L,
        incomeCents = 0L,
        expenseCents = 0L,
        topCategories = emptyList(),
        prevIncomeCents = 0L,
        prevExpenseCents = 0L,
        analysisText = null,
        localInsights = emptyList(),
        status = status,
        generatedAtMs = 0L,
    )
}
