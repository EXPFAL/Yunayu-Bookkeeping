package com.expfal.yunayu.ui.screen.home

import com.expfal.yunayu.domain.model.Account
import com.expfal.yunayu.domain.model.AccountBalance
import com.expfal.yunayu.domain.model.AccountDeleteImpact
import com.expfal.yunayu.domain.model.AccountFilter
import com.expfal.yunayu.domain.model.CategoryExpense
import com.expfal.yunayu.domain.model.RecentTransaction
import com.expfal.yunayu.domain.model.Transaction
import com.expfal.yunayu.domain.model.TransactionType
import com.expfal.yunayu.domain.model.WindowTotals
import com.expfal.yunayu.domain.repository.AccountRepository
import com.expfal.yunayu.domain.repository.TransactionRepository
import com.expfal.yunayu.ui.screen.quickadd.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
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
        val viewModel = createViewModel(transactionRepository = repo)

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
        val viewModel = createViewModel(transactionRepository = repo)

        val state = viewModel.uiState.value
        assertEquals(1, state.recent.size)
        assertEquals("买书", state.recent.single().note)
    }

    @Test
    fun `empty recent list yields empty state`() = runTest {
        val viewModel = createViewModel()

        val state = viewModel.uiState.value
        assertFalse(state.loading)
        assertTrue(state.recent.isEmpty())
    }

    @Test
    fun `new transaction re-emits updated list`() = runTest {
        val repo = FakeTransactionRepository()
        val viewModel = createViewModel(transactionRepository = repo)

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
        val viewModel = createViewModel(transactionRepository = repo)

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
        val viewModel = createViewModel(transactionRepository = repo)

        val state = viewModel.uiState.value
        assertFalse(state.loading)
        assertEquals(1, state.recent.size)
        assertEquals(0L, state.heldCents)
    }

    @Test
    fun `balances by account populate heldByAccount`() = runTest {
        val accountRepo = FakeAccountRepository().apply {
            balancesFlow.value = listOf(
                AccountBalance(accountId = 1L, accountName = "微信", balanceCents = 5_000L),
                AccountBalance(accountId = null, accountName = null, balanceCents = 1_000L),
            )
        }
        val viewModel = createViewModel(accountRepository = accountRepo)

        val state = viewModel.uiState.value
        assertEquals(2, state.heldByAccount.size)
        assertEquals("微信", state.heldByAccount.first().accountName)
        assertEquals(5_000L, state.heldByAccount.first().balanceCents)
    }

    @Test
    fun `balances failure degrades to empty without clearing heldCents`() = runTest {
        val transactionRepo = FakeTransactionRepository().apply { heldCentsFlow.value = 7_500L }
        val accountRepo = FakeAccountRepository().apply {
            balancesOverride = flow { error("balances down") }
        }
        val viewModel = createViewModel(transactionRepository = transactionRepo, accountRepository = accountRepo)

        val state = viewModel.uiState.value
        assertTrue(state.heldByAccount.isEmpty())
        assertEquals(7_500L, state.heldCents)
    }

    @Test
    fun `unspecified-only balances keep state correct`() = runTest {
        val accountRepo = FakeAccountRepository().apply {
            balancesFlow.value = listOf(AccountBalance(accountId = null, accountName = null, balanceCents = -500L))
        }
        val viewModel = createViewModel(accountRepository = accountRepo)

        val state = viewModel.uiState.value
        assertEquals(1, state.heldByAccount.size)
        assertNull(state.heldByAccount.single().accountId)
        assertEquals(-500L, state.heldByAccount.single().balanceCents)
    }

    @Test
    fun `held cents equals sum of account balances including initial balance`() = runTest {
        val transactionRepo = FakeTransactionRepository().apply { heldCentsFlow.value = 15_000L }
        val accountRepo = FakeAccountRepository().apply {
            balancesFlow.value = listOf(
                AccountBalance(accountId = 1L, accountName = "微信", balanceCents = 11_000L),
                AccountBalance(accountId = 2L, accountName = "支付宝", balanceCents = 6_000L),
                AccountBalance(accountId = null, accountName = null, balanceCents = -2_000L),
            )
        }
        val viewModel = createViewModel(transactionRepository = transactionRepo, accountRepository = accountRepo)

        val state = viewModel.uiState.value
        assertEquals(15_000L, state.heldCents)
        // 恒等式：总资金 = Σ账户余额 + 未指定净额（含期初口径下仍自一致）
        assertEquals(state.heldCents, state.heldByAccount.sumOf { it.balanceCents })
    }

    @Test
    fun `initial balance change re-emits held funds and account breakdown`() = runTest {
        val transactionRepo = FakeTransactionRepository()
        val accountRepo = FakeAccountRepository()
        val viewModel = createViewModel(transactionRepository = transactionRepo, accountRepository = accountRepo)

        transactionRepo.heldCentsFlow.value = 5_000L
        accountRepo.balancesFlow.value = listOf(AccountBalance(accountId = 1L, accountName = "微信", balanceCents = 5_000L))
        assertEquals(5_000L, viewModel.uiState.value.heldCents)
        assertEquals(5_000L, viewModel.uiState.value.heldByAccount.single().balanceCents)

        // 修改期初后两条观察链重新发射，持有资金与账户分组同步刷新
        transactionRepo.heldCentsFlow.value = 12_000L
        accountRepo.balancesFlow.value = listOf(AccountBalance(accountId = 1L, accountName = "微信", balanceCents = 12_000L))
        assertEquals(12_000L, viewModel.uiState.value.heldCents)
        assertEquals(12_000L, viewModel.uiState.value.heldByAccount.single().balanceCents)
    }

    @Test
    fun `notifySaved emits Saved event`() = runTest {
        val viewModel = createViewModel()

        val deferred = async { viewModel.events.first() }
        viewModel.notifySaved()
        val event = deferred.await()

        assertTrue(event is HomeEvent.Saved)
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
            accountFilter: AccountFilter,
        ): Flow<List<RecentTransaction>> = flowOf(emptyList())

        override fun observeUncategorizedCount(): Flow<Int> = flowOf(0)

        override suspend fun getUncategorized(): List<RecentTransaction> = emptyList()

        override suspend fun assignTags(assignments: Map<Long, List<Long>>) = Unit

        override suspend fun getOccurredAtsByTagIds(tagIds: List<Long>): List<Long> = emptyList()

        override suspend fun getById(id: Long): Transaction? = null

        override suspend fun updateTransaction(transaction: Transaction) = Unit
    }

    /** [AccountRepository] 手写 fake：以 MutableStateFlow 驱动按账户分组余额。 */
    private class FakeAccountRepository : AccountRepository {

        val balancesFlow = MutableStateFlow<List<AccountBalance>>(emptyList())
        var balancesOverride: Flow<List<AccountBalance>>? = null

        override fun observeAccounts(): Flow<List<Account>> = flowOf(emptyList())

        override suspend fun getAccounts(): List<Account> = emptyList()

        override fun observeBalances(): Flow<List<AccountBalance>> = balancesOverride ?: balancesFlow

        override suspend fun addAccount(name: String): Long = 0L

        override suspend fun addAccount(name: String, initialBalanceCents: Long): Long = 0L

        override suspend fun renameAccount(id: Long, newName: String) = Unit

        override suspend fun updateAccount(id: Long, newName: String, initialBalanceCents: Long) = Unit

        override suspend fun getDeleteImpact(id: Long): AccountDeleteImpact = AccountDeleteImpact(0)

        override suspend fun deleteAccount(id: Long) = Unit

        override fun observeLastUsedAccountId(): Flow<Long?> = flowOf(null)

        override suspend fun saveLastUsedAccountId(id: Long?) = Unit

        override suspend fun updateInitialBalance(accountId: Long, cents: Long) = Unit
    }

    private fun createViewModel(
        transactionRepository: TransactionRepository = FakeTransactionRepository(),
        accountRepository: AccountRepository = FakeAccountRepository(),
    ): HomeViewModel = HomeViewModel(transactionRepository, accountRepository)
}
