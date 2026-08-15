package com.expfal.yunayu.domain.usecase

import com.expfal.yunayu.domain.model.CategoryExpense
import com.expfal.yunayu.domain.model.RecentTransaction
import com.expfal.yunayu.domain.model.Transaction
import com.expfal.yunayu.domain.model.TransactionType
import com.expfal.yunayu.domain.model.WindowTotals
import com.expfal.yunayu.domain.repository.TransactionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** [AddTransactionUseCase] 的 JVM 单元测试（手写 fake 仓储 + coroutines-test）。 */
class AddTransactionUseCaseTest {

    @Test
    fun `creates expense transaction`() = runTest {
        val repository = FakeTransactionRepository()
        val useCase = AddTransactionUseCase(repository)

        useCase(amountCents = 1234L, tagId = 7L, occurredAt = 500L)

        assertEquals(TransactionType.EXPENSE, repository.added.single().type)
    }

    @Test
    fun `creates income transaction when type specified`() = runTest {
        val repository = FakeTransactionRepository()
        val useCase = AddTransactionUseCase(repository)

        useCase(amountCents = 500L, tagId = null, occurredAt = 1L, type = TransactionType.INCOME)

        assertEquals(TransactionType.INCOME, repository.added.single().type)
    }

    @Test
    fun `passes amountCents tagId and occurredAt through`() = runTest {
        val repository = FakeTransactionRepository()
        val useCase = AddTransactionUseCase(repository)

        useCase(amountCents = 2500L, tagId = 3L, occurredAt = 900L)

        val transaction = repository.added.single()
        assertEquals(2500L, transaction.amountCents)
        assertEquals(3L, transaction.tagId)
        assertEquals(900L, transaction.occurredAt)
        assertEquals(null, transaction.note)
    }

    @Test
    fun `delegates to repository exactly once and returns its result`() = runTest {
        val repository = FakeTransactionRepository().apply { nextId = 42L }
        val useCase = AddTransactionUseCase(repository)

        val id = useCase(amountCents = 100L, tagId = null, occurredAt = 1L)

        assertEquals(42L, id)
        assertEquals(1, repository.added.size)
    }

    /** [TransactionRepository] 手写 fake：记录 add 入参，返回预置主键。 */
    private class FakeTransactionRepository : TransactionRepository {

        val added = mutableListOf<Transaction>()
        var nextId: Long = 0L

        override suspend fun add(transaction: Transaction): Long {
            added += transaction
            return nextId
        }

        override fun observeAll(): Flow<List<Transaction>> = flowOf(emptyList())

        override fun observeByTag(tagId: Long): Flow<List<Transaction>> = flowOf(emptyList())

        override fun observeExpenseSumBetween(
            startInclusiveMs: Long,
            endExclusiveMs: Long,
        ): Flow<Long> = flowOf(0L)

        override fun observeHeldCents(): Flow<Long> = flowOf(0L)

        override suspend fun getWindowTotals(
            startInclusiveMs: Long,
            endExclusiveMs: Long,
        ): WindowTotals = WindowTotals(0L, 0L)

        override suspend fun getExpenseByCategory(
            startInclusiveMs: Long,
            endExclusiveMs: Long,
        ): List<CategoryExpense> = emptyList()

        override fun observeRecent(limit: Int): Flow<List<RecentTransaction>> =
            flowOf(emptyList())
    }
}
