package com.expfal.yunayu.domain.report

import com.expfal.yunayu.domain.model.AccountFilter
import com.expfal.yunayu.domain.model.CategoryExpense
import com.expfal.yunayu.domain.model.RecentTransaction
import com.expfal.yunayu.domain.model.Transaction
import com.expfal.yunayu.domain.model.TransactionType
import com.expfal.yunayu.domain.model.WindowTotals
import com.expfal.yunayu.domain.report.model.CategoryShare
import com.expfal.yunayu.domain.repository.TransactionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LoadCategoryExpenseDetailUseCaseTest {

    @Test
    fun `loads tag detail with count and recent expenses`() = runTest {
        val txRepo = FakeTransactionRepository(
            filtered = listOf(
                RecentTransaction(1L, 1_000L, TransactionType.EXPENSE, "餐饮", 300L),
                RecentTransaction(2L, 500L, TransactionType.EXPENSE, "餐饮", 200L),
                RecentTransaction(3L, 200L, TransactionType.INCOME, "餐饮", 100L),
            ),
        )
        val useCase = LoadCategoryExpenseDetailUseCase(txRepo)
        val share = CategoryShare("餐饮", 1_500L, 50, tagId = 7L)

        val detail = useCase(
            windowStartMs = 0L,
            windowEndMs = 1_000L,
            totalExpenseCents = 3_000L,
            windowDayCount = 10,
            share = share,
            isOtherBucket = false,
        )

        assertEquals(1_500L, detail.expenseCents)
        assertEquals(50, detail.percent)
        assertEquals(2, detail.txCount)
        assertEquals(150L, detail.dailyAvgCents)
        assertEquals(listOf(1L, 2L), detail.recent.map { it.id })
        assertEquals(listOf(7L), txRepo.lastTagIds)
    }

    @Test
    fun `other bucket expands remaining shares beyond top N`() = runTest {
        val categories = (1..10).map { i ->
            CategoryExpense("c$i", 100L, tagId = i.toLong())
        }
        val txRepo = FakeTransactionRepository(
            categoryExpenses = categories,
            filtered = emptyList(),
        )
        val useCase = LoadCategoryExpenseDetailUseCase(txRepo)
        val other = CategoryShare("其他", 200L, 20)

        val detail = useCase(
            windowStartMs = 0L,
            windowEndMs = 1L,
            totalExpenseCents = 1_000L,
            windowDayCount = 5,
            share = other,
            isOtherBucket = true,
        )

        assertEquals(2, detail.remainingShares.size)
        assertEquals(listOf("c9", "c10"), detail.remainingShares.map { it.tagName })
        assertEquals(200L, detail.expenseCents)
        assertEquals(20, detail.percent)
        assertEquals(40L, detail.dailyAvgCents)
        assertTrue(detail.remainingShares.all { it.tagId != null })
    }

    private class FakeTransactionRepository(
        private val filtered: List<RecentTransaction> = emptyList(),
        private val categoryExpenses: List<CategoryExpense> = emptyList(),
    ) : TransactionRepository {
        var lastTagIds: List<Long> = emptyList()

        override suspend fun add(transaction: Transaction): Long = 0L
        override suspend fun delete(transactionId: Long) = Unit
        override suspend fun getById(id: Long): Transaction? = null
        override suspend fun updateTransaction(transaction: Transaction) = Unit
        override fun observeAll(): Flow<List<Transaction>> = flowOf(emptyList())
        override fun observeByTag(tagId: Long): Flow<List<Transaction>> = flowOf(emptyList())
        override fun observeExpenseSumBetween(startInclusiveMs: Long, endExclusiveMs: Long) = flowOf(0L)
        override fun observeHeldCents() = flowOf(0L)
        override suspend fun getWindowTotals(startInclusiveMs: Long, endExclusiveMs: Long) =
            WindowTotals(0L, 0L)
        override suspend fun getExpenseByCategory(
            startInclusiveMs: Long,
            endExclusiveMs: Long,
        ): List<CategoryExpense> = categoryExpenses
        override suspend fun countUncategorizedBetween(startInclusiveMs: Long, endExclusiveMs: Long) = 0
        override suspend fun getMaxExpenseCentsBetween(startInclusiveMs: Long, endExclusiveMs: Long) = null
        override fun observeRecent(limit: Int) = flowOf(emptyList<RecentTransaction>())
        override fun observeFiltered(
            startInclusiveMs: Long?,
            endExclusiveMs: Long?,
            tagIds: List<Long>,
            noteKeyword: String?,
            accountFilter: AccountFilter,
        ): Flow<List<RecentTransaction>> {
            lastTagIds = tagIds
            return flowOf(filtered)
        }
        override fun observeUncategorizedCount() = flowOf(0)
        override suspend fun getUncategorized() = emptyList<RecentTransaction>()
        override suspend fun assignTags(assignments: Map<Long, List<Long>>) = Unit
        override suspend fun getOccurredAtsByTagIds(tagIds: List<Long>) = emptyList<Long>()
    }
}
