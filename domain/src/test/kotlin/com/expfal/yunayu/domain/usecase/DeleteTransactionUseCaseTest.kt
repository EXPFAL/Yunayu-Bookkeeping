package com.expfal.yunayu.domain.usecase

import com.expfal.yunayu.domain.model.CategoryExpense
import com.expfal.yunayu.domain.model.RecentTransaction
import com.expfal.yunayu.domain.model.Transaction
import com.expfal.yunayu.domain.model.WindowTotals
import com.expfal.yunayu.domain.report.model.Report
import com.expfal.yunayu.domain.report.model.ReportPeriodType
import com.expfal.yunayu.domain.repository.ReportRepository
import com.expfal.yunayu.domain.repository.TransactionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** [DeleteTransactionUseCase] 的 JVM 单元测试（手写 fake 仓储 + coroutines-test）。 */
class DeleteTransactionUseCaseTest {

    @Test
    fun `deletes transaction then invalidates covering reports`() = runTest {
        val txRepo = FakeTransactionRepository()
        val reportRepo = FakeReportRepository()
        val useCase = DeleteTransactionUseCase(txRepo, reportRepo)

        useCase(42L, 900L)

        assertEquals(listOf(42L), txRepo.deletedIds)
        assertEquals(listOf(900L), reportRepo.invalidatedEpochMillis)
    }

    @Test
    fun `rethrows delete failure and skips invalidation`() = runTest {
        val txRepo = FakeTransactionRepository().apply { deleteError = IllegalStateException("db down") }
        val reportRepo = FakeReportRepository()
        val useCase = DeleteTransactionUseCase(txRepo, reportRepo)

        var caught: Throwable? = null
        try {
            useCase(42L, 900L)
        } catch (e: Throwable) {
            caught = e
        }

        assertTrue(caught is IllegalStateException)
        assertTrue(reportRepo.invalidatedEpochMillis.isEmpty())
    }

    @Test
    fun `swallows invalidation failure and still completes`() = runTest {
        val txRepo = FakeTransactionRepository()
        val reportRepo = FakeReportRepository().apply { invalidateError = RuntimeException("update failed") }
        val useCase = DeleteTransactionUseCase(txRepo, reportRepo)

        useCase(42L, 900L)

        assertEquals(listOf(42L), txRepo.deletedIds)
        assertEquals(listOf(900L), reportRepo.invalidatedEpochMillis)
    }

    /** [TransactionRepository] 手写 fake：记录删除 id，可配置删除异常。 */
    private class FakeTransactionRepository : TransactionRepository {

        val deletedIds = mutableListOf<Long>()
        var deleteError: Throwable? = null

        override suspend fun add(transaction: Transaction): Long = 0L

        override suspend fun delete(transactionId: Long) {
            deleteError?.let { throw it }
            deletedIds += transactionId
        }

        override fun observeAll(): Flow<List<Transaction>> = flowOf(emptyList())

        override fun observeByTag(tagId: Long): Flow<List<Transaction>> = flowOf(emptyList())

        override fun observeExpenseSumBetween(startInclusiveMs: Long, endExclusiveMs: Long): Flow<Long> = flowOf(0L)

        override fun observeHeldCents(): Flow<Long> = flowOf(0L)

        override suspend fun getWindowTotals(
            startInclusiveMs: Long,
            endExclusiveMs: Long,
        ): WindowTotals = WindowTotals(0L, 0L)

        override suspend fun getExpenseByCategory(
            startInclusiveMs: Long,
            endExclusiveMs: Long,
        ): List<CategoryExpense> = emptyList()

        override fun observeRecent(limit: Int): Flow<List<RecentTransaction>> = flowOf(emptyList())

        override fun observeFiltered(
            startInclusiveMs: Long?,
            endExclusiveMs: Long?,
            tagIds: List<Long>,
            noteKeyword: String?,
        ): Flow<List<RecentTransaction>> = flowOf(emptyList())
    }

    /** [ReportRepository] 手写 fake：记录标脏入参，可配置标脏异常。 */
    private class FakeReportRepository : ReportRepository {

        val invalidatedEpochMillis = mutableListOf<Long>()
        var invalidateError: Throwable? = null

        override fun observeByType(type: ReportPeriodType): Flow<List<Report>> = flowOf(emptyList())

        override suspend fun getByKey(periodType: ReportPeriodType, periodKey: String): Report? = null

        override suspend fun upsert(report: Report) = Unit

        override suspend fun invalidateWhereWindowContains(epochMillis: Long) {
            invalidatedEpochMillis += epochMillis
            invalidateError?.let { throw it }
        }
    }
}
