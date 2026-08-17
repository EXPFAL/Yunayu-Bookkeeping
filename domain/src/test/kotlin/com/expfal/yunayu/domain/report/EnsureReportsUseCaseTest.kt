package com.expfal.yunayu.domain.report

import com.expfal.yunayu.domain.model.AccountFilter
import com.expfal.yunayu.domain.model.CategoryExpense
import com.expfal.yunayu.domain.model.RecentTransaction
import com.expfal.yunayu.domain.model.Transaction
import com.expfal.yunayu.domain.model.WindowTotals
import com.expfal.yunayu.domain.report.model.Report
import com.expfal.yunayu.domain.report.model.ReportPeriodType
import com.expfal.yunayu.domain.report.model.ReportStatus
import com.expfal.yunayu.domain.repository.ReportRepository
import com.expfal.yunayu.domain.repository.TransactionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.LocalDate

/** [EnsureReportsUseCase] 的 JVM 单元测试（真实 [GenerateReportUseCase] + 手写 fake 仓储/分析器）。 */
class EnsureReportsUseCaseTest {

    @Test
    fun `ensures previous month report when missing`() = runTest {
        val reportRepository = FakeReportRepository()
        val useCase = EnsureReportsUseCase(
            reportRepository,
            GenerateReportUseCase(FakeTransactionRepository(), reportRepository, FakeReportAnalyzer()),
        )

        useCase.ensure(LocalDate.of(2026, 8, 15))

        val upserted = reportRepository.upserted
        assertEquals(2, upserted.size) // 周报 + 月报
        val monthly = upserted.find { it.periodType == ReportPeriodType.MONTHLY }
        assertEquals("2026-07", monthly?.periodKey)
        val weekly = upserted.find { it.periodType == ReportPeriodType.WEEKLY }
        assertEquals("2026-W32", weekly?.periodKey)
    }

    @Test
    fun `ensures annual report only in january`() = runTest {
        val reportRepository = FakeReportRepository()
        val useCase = EnsureReportsUseCase(
            reportRepository,
            GenerateReportUseCase(FakeTransactionRepository(), reportRepository, FakeReportAnalyzer()),
        )

        useCase.ensure(LocalDate.of(2026, 1, 15))

        val upserted = reportRepository.upserted
        assertEquals(3, upserted.size) // 周报 + 月报 + 年报
        assertEquals(
            setOf(
                ReportPeriodType.WEEKLY to "2026-W02",
                ReportPeriodType.MONTHLY to "2025-12",
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
        val useCase = EnsureReportsUseCase(
            reportRepository,
            GenerateReportUseCase(FakeTransactionRepository(), reportRepository, FakeReportAnalyzer()),
        )

        useCase.ensure(LocalDate.of(2026, 8, 15))

        assertEquals(1, reportRepository.upserted.size) // 只生成周报，月报已存在
    }

    @Test
    fun `caps at two reports serially in january`() = runTest {
        val reportRepository = FakeReportRepository()
        val useCase = EnsureReportsUseCase(
            reportRepository,
            GenerateReportUseCase(FakeTransactionRepository(), reportRepository, FakeReportAnalyzer()),
        )

        useCase.ensure(LocalDate.of(2026, 1, 15))

        assertEquals(3, reportRepository.upserted.size) // 周报 + 月报 + 年报
    }

    @Test
    fun `skips annual outside january`() = runTest {
        val reportRepository = FakeReportRepository()
        val useCase = EnsureReportsUseCase(
            reportRepository,
            GenerateReportUseCase(FakeTransactionRepository(), reportRepository, FakeReportAnalyzer()),
        )

        useCase.ensure(LocalDate.of(2026, 12, 15))

        assertEquals(2, reportRepository.upserted.size) // 周报 + 月报
        val monthly = reportRepository.upserted.find { it.periodType == ReportPeriodType.MONTHLY }
        assertEquals("2026-11", monthly?.periodKey)
        val weekly = reportRepository.upserted.find { it.periodType == ReportPeriodType.WEEKLY }
        assertEquals("2026-W50", weekly?.periodKey)
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
        }

        override suspend fun invalidateWhereWindowContains(epochMillis: Long) = Unit
    }

    /** [ReportAnalyzer] 手写 fake：始终可用并返回固定分析文本。 */
    private class FakeReportAnalyzer : ReportAnalyzer {
        override suspend fun isAvailable(): Boolean = true

        override suspend fun analyze(systemInstruction: String, dataText: String): String = "分析结论"
    }

    @Test
    fun `ensures previous week report when missing`() = runTest {
        val reportRepository = FakeReportRepository()
        val useCase = EnsureReportsUseCase(
            reportRepository,
            GenerateReportUseCase(FakeTransactionRepository(), reportRepository, FakeReportAnalyzer()),
        )

        // 2026-08-17 是周一，上周是 2026-08-10 至 2026-08-17
        useCase.ensure(LocalDate.of(2026, 8, 17))

        val upserted = reportRepository.upserted
        assertEquals(2, upserted.size) // 周报 + 月报
        val weekly = upserted.find { it.periodType == ReportPeriodType.WEEKLY }
        assertEquals("2026-W33", weekly?.periodKey)
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
        status = status,
        generatedAtMs = 0L,
    )
}
