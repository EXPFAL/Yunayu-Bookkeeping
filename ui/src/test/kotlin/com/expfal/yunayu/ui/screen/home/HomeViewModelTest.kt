package com.expfal.yunayu.ui.screen.home

import com.expfal.yunayu.domain.model.CategoryExpense
import com.expfal.yunayu.domain.model.RecentTransaction
import com.expfal.yunayu.domain.model.Transaction
import com.expfal.yunayu.domain.model.TransactionType
import com.expfal.yunayu.domain.model.WindowTotals
import com.expfal.yunayu.domain.repository.TransactionRepository
import com.expfal.yunayu.ui.screen.quickadd.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

/** [HomeViewModel] 的 JVM 单元测试（手写 fake 仓储 + coroutines-test）。 */
@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    @JvmField
    @RegisterExtension
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `maps recent transactions into state`() = runTest {
        val repo = FakeTransactionRepository().apply {
            recentFlow.value = listOf(recent(id = 1L, tagName = "学习", amountCents = 1_500L))
        }
        val viewModel = HomeViewModel(repo)

        val state = viewModel.uiState.value
        assertFalse(state.loading)
        assertEquals(1, state.recent.size)
        assertEquals("学习", state.recent.single().tagName)
        assertEquals(1_500L, state.recent.single().amountCents)
    }

    @Test
    fun `recent note passes through to state`() = runTest {
        val repo = FakeTransactionRepository().apply {
            recentFlow.value = listOf(recent(id = 1L, tagName = "学习", note = "买书"))
        }
        val viewModel = HomeViewModel(repo)

        val state = viewModel.uiState.value
        assertEquals(1, state.recent.size)
        assertEquals("买书", state.recent.single().note)
    }

    @Test
    fun `empty recent list yields empty state`() = runTest {
        val viewModel = HomeViewModel(FakeTransactionRepository())

        val state = viewModel.uiState.value
        assertFalse(state.loading)
        assertTrue(state.recent.isEmpty())
    }

    @Test
    fun `new transaction re-emits updated list`() = runTest {
        val repo = FakeTransactionRepository()
        val viewModel = HomeViewModel(repo)

        assertTrue(viewModel.uiState.value.recent.isEmpty())

        repo.recentFlow.value = listOf(
            recent(id = 1L, tagName = "学习"),
            recent(id = 2L, tagName = "社交"),
        )

        assertEquals(2, viewModel.uiState.value.recent.size)
        assertEquals(listOf(1L, 2L), viewModel.uiState.value.recent.map { it.id })
    }

    @Test
    fun `combines held cents into state`() = runTest {
        val repo = FakeTransactionRepository().apply { heldCentsFlow.value = 7_500L }
        val viewModel = HomeViewModel(repo)

        val state = viewModel.uiState.value
        assertFalse(state.loading)
        assertEquals(7_500L, state.heldCents)
    }

    @Test
    fun `held failure does not clear recent`() = runTest {
        val repo = FakeTransactionRepository().apply {
            recentFlow.value = listOf(recent(id = 1L, tagName = "学习"))
            heldCentsOverride = flow { error("held down") }
        }
        val viewModel = HomeViewModel(repo)

        val state = viewModel.uiState.value
        assertFalse(state.loading)
        assertEquals(1, state.recent.size)
        assertEquals(0L, state.heldCents)
    }

    private fun recent(
        id: Long,
        tagName: String? = null,
        amountCents: Long = 1_000L,
        note: String? = null,
    ) = RecentTransaction(
        id = id,
        amountCents = amountCents,
        type = TransactionType.EXPENSE,
        tagName = tagName,
        occurredAt = 0L,
        note = note,
    )

    /** [TransactionRepository] 手写 fake：以 MutableStateFlow 驱动最近列表。 */
    private class FakeTransactionRepository : TransactionRepository {

        val recentFlow = MutableStateFlow<List<RecentTransaction>>(emptyList())
        val heldCentsFlow = MutableStateFlow(0L)
        var heldCentsOverride: Flow<Long>? = null

        override suspend fun add(transaction: Transaction): Long = 0L

        override suspend fun delete(transactionId: Long) = Unit

        override fun observeAll(): Flow<List<Transaction>> = flowOf(emptyList())

        override fun observeByTag(tagId: Long): Flow<List<Transaction>> = flowOf(emptyList())

        override fun observeExpenseSumBetween(startInclusiveMs: Long, endExclusiveMs: Long): Flow<Long> = flowOf(0L)

        override fun observeHeldCents(): Flow<Long> = heldCentsOverride ?: heldCentsFlow

        override suspend fun getWindowTotals(
            startInclusiveMs: Long,
            endExclusiveMs: Long,
        ): WindowTotals = WindowTotals(0L, 0L)

        override suspend fun getExpenseByCategory(
            startInclusiveMs: Long,
            endExclusiveMs: Long,
        ): List<CategoryExpense> = emptyList()

        override fun observeRecent(limit: Int): Flow<List<RecentTransaction>> = recentFlow

        override fun observeFiltered(
            startInclusiveMs: Long?,
            endExclusiveMs: Long?,
            tagIds: List<Long>,
            noteKeyword: String?,
        ): Flow<List<RecentTransaction>> = flowOf(emptyList())

        override fun observeUncategorizedCount(): Flow<Int> = flowOf(0)

        override suspend fun getUncategorized(): List<RecentTransaction> = emptyList()

        override suspend fun assignTags(assignments: Map<Long, List<Long>>) = Unit

        override suspend fun getOccurredAtsByTagIds(tagIds: List<Long>): List<Long> = emptyList()
    }
}
