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
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** [GenerateReportUseCase] 的 JVM 单元测试（手写 fake 仓储）。 */
class GenerateReportUseCaseTest {

    private val currentTotals = WindowTotals(incomeCents = 5_000L, expenseCents = 3_000L)
    private val prevTotals = WindowTotals(incomeCents = 4_000L, expenseCents = 2_500L)

    @Test
    fun `persists success report with structured data and null analysis`() = runTest {
        val transactionRepository = FakeTransactionRepository(
            currentTotals = currentTotals,
            prevTotals = prevTotals,
            categoryExpenses = listOf(
                CategoryExpense("餐饮", 1_500L, tagId = 1L),
                CategoryExpense(null, 500L),
            ),
        )
        val reportRepository = FakeReportRepository()
        val useCase = newUseCase(transactionRepository, reportRepository)

        useCase(MONTHLY, "2026-07", 100L, 200L, 0L, 100L)

        val report = reportRepository.upserted.single()
        assertEquals(ReportStatus.SUCCESS, report.status)
        assertNull(report.analysisText)
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
    fun `regenerate reuses existing report id and clears analysis text`() = runTest {
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
        val useCase = newUseCase(FakeTransactionRepository(), reportRepository)

        useCase(MONTHLY, "2026-07", 100L, 200L, 0L, 100L)

        val report = reportRepository.upserted.single()
        assertEquals(42L, report.id)
        assertEquals(ReportStatus.SUCCESS, report.status)
        assertNull(report.analysisText)
    }

    private fun newUseCase(
        tx: TransactionRepository,
        reports: ReportRepository,
    ) = GenerateReportUseCase(tx, reports, FakeMonthlyBudgetRepository())

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
    ) : TransactionRepository {

        val windowTotalsCalls = mutableListOf<Pair<Long, Long>>()

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
        ): List<CategoryExpense> = categoryExpenses

        override suspend fun countUncategorizedBetween(startInclusiveMs: Long, endExclusiveMs: Long): Int = 0

        override suspend fun getMaxExpenseCentsBetween(startInclusiveMs: Long, endExclusiveMs: Long): Long? = null
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
}
