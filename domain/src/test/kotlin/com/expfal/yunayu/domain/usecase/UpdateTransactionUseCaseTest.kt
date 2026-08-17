package com.expfal.yunayu.domain.usecase

import com.expfal.yunayu.domain.model.AccountFilter
import com.expfal.yunayu.domain.model.CategoryExpense
import com.expfal.yunayu.domain.model.RecentTransaction
import com.expfal.yunayu.domain.model.Transaction
import com.expfal.yunayu.domain.model.TransactionType
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

/** [UpdateTransactionUseCase] 的 JVM 单元测试（手写 fake 仓储 + coroutines-test）。 */
class UpdateTransactionUseCaseTest {

    @Test
    fun `updates transaction then invalidates covering reports`() = runTest {
        val txRepo = FakeTransactionRepository()
        val reportRepo = FakeReportRepository()
        val useCase = UpdateTransactionUseCase(txRepo, reportRepo)
        val transaction = transaction(id = 42L, occurredAt = 900L)

        useCase(transaction)

        assertEquals(listOf(transaction), txRepo.updated)
        assertEquals(listOf(900L), reportRepo.invalidatedEpochMillis)
    }

    @Test
    fun `rejects zero id without touching repository`() = runTest {
        val txRepo = FakeTransactionRepository()
        val reportRepo = FakeReportRepository()
        val useCase = UpdateTransactionUseCase(txRepo, reportRepo)

        var caught: Throwable? = null
        try {
            useCase(transaction(id = 0L))
        } catch (e: Throwable) {
            caught = e
        }

        assertTrue(caught is IllegalArgumentException)
        assertTrue(txRepo.updated.isEmpty())
        assertTrue(reportRepo.invalidatedEpochMillis.isEmpty())
    }

    @Test
    fun `rejects non-positive amount without touching repository`() = runTest {
        val txRepo = FakeTransactionRepository()
        val reportRepo = FakeReportRepository()
        val useCase = UpdateTransactionUseCase(txRepo, reportRepo)

        var caught: Throwable? = null
        try {
            useCase(transaction(id = 42L, amountCents = 0L))
        } catch (e: Throwable) {
            caught = e
        }

        assertTrue(caught is IllegalArgumentException)
        assertTrue(txRepo.updated.isEmpty())
        assertTrue(reportRepo.invalidatedEpochMillis.isEmpty())
    }

    @Test
    fun `accepts both known transaction types`() = runTest {
        val txRepo = FakeTransactionRepository()
        val useCase = UpdateTransactionUseCase(txRepo, FakeReportRepository())

        useCase(transaction(id = 1L, type = TransactionType.EXPENSE))
        useCase(transaction(id = 2L, type = TransactionType.INCOME))

        assertEquals(2, txRepo.updated.size)
    }

    @Test
    fun `rethrows update failure and skips invalidation`() = runTest {
        val txRepo = FakeTransactionRepository().apply { updateError = IllegalStateException("db down") }
        val reportRepo = FakeReportRepository()
        val useCase = UpdateTransactionUseCase(txRepo, reportRepo)

        var caught: Throwable? = null
        try {
            useCase(transaction(id = 42L))
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
        val useCase = UpdateTransactionUseCase(txRepo, reportRepo)
        val transaction = transaction(id = 42L, occurredAt = 900L)

        useCase(transaction)

        assertEquals(listOf(transaction), txRepo.updated)
        assertEquals(listOf(900L), reportRepo.invalidatedEpochMillis)
    }

    private fun transaction(
        id: Long,
        amountCents: Long = 1_000L,
        type: TransactionType = TransactionType.EXPENSE,
        occurredAt: Long = 0L,
    ) = Transaction(
        id = id,
        amountCents = amountCents,
        type = type,
        note = null,
        tagId = null,
        accountId = null,
        occurredAt = occurredAt,
    )

    /** [TransactionRepository] 手写 fake：记录 updateTransaction 入参，可配置更新异常。 */
    private class FakeTransactionRepository : TransactionRepository {

        val updated = mutableListOf<Transaction>()
        var updateError: Throwable? = null

        override suspend fun add(transaction: Transaction): Long = 0L

        override suspend fun delete(transactionId: Long) = Unit

        override suspend fun getById(id: Long): Transaction? = null

        override suspend fun updateTransaction(transaction: Transaction) {
            updateError?.let { throw it }
            updated += transaction
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
            accountFilter: AccountFilter,
        ): Flow<List<RecentTransaction>> = flowOf(emptyList())

        override fun observeUncategorizedCount(): Flow<Int> = flowOf(0)

        override suspend fun getUncategorized(): List<RecentTransaction> = emptyList()

        override suspend fun assignTags(assignments: Map<Long, List<Long>>) = Unit

        override suspend fun getOccurredAtsByTagIds(tagIds: List<Long>): List<Long> = emptyList()
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
