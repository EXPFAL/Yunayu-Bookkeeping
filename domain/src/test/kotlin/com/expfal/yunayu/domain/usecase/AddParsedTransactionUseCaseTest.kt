package com.expfal.yunayu.domain.usecase

import com.expfal.yunayu.domain.model.AccountFilter
import com.expfal.yunayu.domain.model.CategoryExpense
import com.expfal.yunayu.domain.model.RecentTransaction
import com.expfal.yunayu.domain.model.Transaction
import com.expfal.yunayu.domain.model.TransactionType
import com.expfal.yunayu.domain.model.WindowTotals
import com.expfal.yunayu.domain.nl.model.NlTransactionDraft
import com.expfal.yunayu.domain.repository.TransactionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** [AddParsedTransactionUseCase] 的 JVM 单元测试（手写 fake 仓储 + coroutines-test）。 */
class AddParsedTransactionUseCaseTest {

    @Test
    fun `maps draft fields to transaction`() = runTest {
        val repository = FakeTransactionRepository()
        val useCase = AddParsedTransactionUseCase(repository)

        useCase(
            NlTransactionDraft(
                amountCents = 2500L,
                type = TransactionType.EXPENSE,
                tagPhrase = "生活·餐饮",
                note = "午饭",
                occurredAtEpochMillis = 900L,
                tagId = 11L,
                accountId = 9L,
            ),
        )

        val transaction = repository.added.single()
        assertEquals(2500L, transaction.amountCents)
        assertEquals(TransactionType.EXPENSE, transaction.type)
        assertEquals("午饭", transaction.note)
        assertEquals(11L, transaction.tagId)
        assertEquals(9L, transaction.accountId)
        assertEquals(900L, transaction.occurredAt)
    }

    @Test
    fun `preserves income type and null note and tagId`() = runTest {
        val repository = FakeTransactionRepository()
        val useCase = AddParsedTransactionUseCase(repository)

        useCase(
            NlTransactionDraft(
                amountCents = 100L,
                type = TransactionType.INCOME,
                note = null,
                occurredAtEpochMillis = 1L,
                tagId = null,
            ),
        )

        val transaction = repository.added.single()
        assertEquals(TransactionType.INCOME, transaction.type)
        assertEquals(null, transaction.note)
        assertEquals(null, transaction.tagId)
        assertEquals(null, transaction.accountId)
    }

    @Test
    fun `delegates to repository exactly once and returns its result`() = runTest {
        val repository = FakeTransactionRepository().apply { nextId = 42L }
        val useCase = AddParsedTransactionUseCase(repository)

        val id = useCase(NlTransactionDraft(amountCents = 500L, occurredAtEpochMillis = 2L))

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

        override suspend fun delete(transactionId: Long) = Unit

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
}
