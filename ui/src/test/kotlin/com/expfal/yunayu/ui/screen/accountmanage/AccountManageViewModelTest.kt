package com.expfal.yunayu.ui.screen.accountmanage

import com.expfal.yunayu.domain.model.Account
import com.expfal.yunayu.domain.model.AccountBalance
import com.expfal.yunayu.domain.model.AccountDeleteImpact
import com.expfal.yunayu.domain.model.DuplicateAccountNameException
import com.expfal.yunayu.domain.repository.AccountRepository
import com.expfal.yunayu.ui.screen.quickadd.MainDispatcherRule
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

/** [AccountManageViewModel] 的 JVM 单元测试（手写 fake 仓储 + coroutines-test）。 */
@OptIn(ExperimentalCoroutinesApi::class)
class AccountManageViewModelTest {

    @JvmField
    @RegisterExtension
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `loads accounts with per account transaction counts`() = runTest {
        val repo = FakeAccountRepository()
        repo.accountsFlow.value = listOf(account(1, "微信"), account(2, "支付宝"))
        repo.impactById = mapOf(1L to AccountDeleteImpact(3), 2L to AccountDeleteImpact(0))
        val vm = viewModel(repo)
        runCurrent()

        val state = vm.uiState.value
        assertFalse(state.loading)
        assertEquals(listOf(1L, 2L), state.accounts.map { it.account.id })
        assertEquals(listOf(3, 0), state.accounts.map { it.transactionCount })
    }

    @Test
    fun `addAccount success emits Added and clears busy`() = runTest {
        val repo = FakeAccountRepository()
        val vm = viewModel(repo)
        runCurrent()

        val events = mutableListOf<AccountManageEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            vm.events.collect { events.add(it) }
        }

        vm.addAccount("微信", 0L)
        runCurrent()

        assertEquals(listOf("微信"), repo.added)
        assertTrue(repo.initialBalanceUpdates.isEmpty())
        assertEquals(listOf(AccountManageEvent.Added), events)
        assertNull(vm.uiState.value.errorMessage)
        assertFalse(vm.uiState.value.busy)
    }

    @Test
    fun `addAccount duplicate name maps friendly error without crash`() = runTest {
        val repo = FakeAccountRepository().apply { addError = DuplicateAccountNameException("dup") }
        val vm = viewModel(repo)
        runCurrent()

        vm.addAccount("微信", 0L)
        runCurrent()

        assertEquals("同名账户已存在", vm.uiState.value.errorMessage)
        assertFalse(vm.uiState.value.busy)
    }

    @Test
    fun `addAccount illegal argument maps repository message`() = runTest {
        val repo = FakeAccountRepository().apply { addError = IllegalArgumentException("账户名不可为空") }
        val vm = viewModel(repo)
        runCurrent()

        vm.addAccount(" ", 0L)
        runCurrent()

        assertEquals("账户名不可为空", vm.uiState.value.errorMessage)
    }

    @Test
    fun `addAccount with initial balance delegates add then updateInitialBalance`() = runTest {
        val repo = FakeAccountRepository().apply { addResultId = 42L }
        val vm = viewModel(repo)
        runCurrent()

        vm.addAccount("微信", 12_345L)
        runCurrent()

        assertEquals(listOf("微信"), repo.added)
        assertEquals(listOf(42L to 12_345L), repo.initialBalanceUpdates)
        assertNull(vm.uiState.value.errorMessage)
        assertFalse(vm.uiState.value.busy)
    }

    @Test
    fun `addAccount skips updateInitialBalance when initial balance is zero`() = runTest {
        val repo = FakeAccountRepository()
        val vm = viewModel(repo)
        runCurrent()

        vm.addAccount("微信", 0L)
        runCurrent()

        assertEquals(listOf("微信"), repo.added)
        assertTrue(repo.initialBalanceUpdates.isEmpty())
    }

    @Test
    fun `addAccount rejects negative initial balance without writing`() = runTest {
        val repo = FakeAccountRepository()
        val vm = viewModel(repo)
        runCurrent()

        vm.addAccount("微信", -1L)
        runCurrent()

        assertEquals("期初余额不能为负", vm.uiState.value.errorMessage)
        assertTrue(repo.added.isEmpty())
        assertTrue(repo.initialBalanceUpdates.isEmpty())
        assertFalse(vm.uiState.value.busy)
    }

    @Test
    fun `addAccount atomic failure on balance write leaves no partial writes`() = runTest {
        val repo = FakeAccountRepository().apply { addBalanceError = RuntimeException("balance write failed") }
        val vm = viewModel(repo)
        runCurrent()

        vm.addAccount("微信", 12_345L)
        runCurrent()

        assertEquals("操作失败，请重试", vm.uiState.value.errorMessage)
        assertTrue(repo.added.isEmpty())
        assertTrue(repo.initialBalanceUpdates.isEmpty())
        assertFalse(vm.uiState.value.busy)
    }

    @Test
    fun `updateAccount saves name and initial balance and closes edit state`() = runTest {
        val repo = FakeAccountRepository()
        val vm = viewModel(repo)
        runCurrent()

        val events = mutableListOf<AccountManageEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            vm.events.collect { events.add(it) }
        }

        val target = account(5, "旧名")
        vm.requestEdit(target)
        vm.updateAccount(5, "新名", 8_000L)
        runCurrent()

        assertEquals(listOf(5L to "新名"), repo.renamed)
        assertEquals(listOf(5L to 8_000L), repo.initialBalanceUpdates)
        assertEquals(listOf(AccountManageEvent.Updated), events)
        assertNull(vm.uiState.value.editingAccount)
        assertFalse(vm.uiState.value.busy)
    }

    @Test
    fun `updateAccount duplicate name keeps edit state with inline error`() = runTest {
        val repo = FakeAccountRepository().apply { renameError = DuplicateAccountNameException("dup") }
        val vm = viewModel(repo)
        runCurrent()

        vm.requestEdit(account(5, "旧名"))
        vm.updateAccount(5, "微信", 0L)
        runCurrent()

        assertEquals("同名账户已存在", vm.uiState.value.errorMessage)
        assertEquals(5L, vm.uiState.value.editingAccount?.id)
        assertTrue(repo.initialBalanceUpdates.isEmpty())
    }

    @Test
    fun `updateAccount rejects negative initial balance keeping edit state`() = runTest {
        val repo = FakeAccountRepository()
        val vm = viewModel(repo)
        runCurrent()

        vm.requestEdit(account(5, "旧名"))
        vm.updateAccount(5, "旧名", -5L)
        runCurrent()

        assertEquals("期初余额不能为负", vm.uiState.value.errorMessage)
        assertTrue(repo.renamed.isEmpty())
        assertTrue(repo.initialBalanceUpdates.isEmpty())
        assertEquals(5L, vm.uiState.value.editingAccount?.id)
        assertFalse(vm.uiState.value.busy)
    }

    @Test
    fun `updateAccount atomic failure on balance write leaves no partial writes`() = runTest {
        val repo = FakeAccountRepository().apply { updateBalanceError = RuntimeException("balance write failed") }
        val vm = viewModel(repo)
        runCurrent()

        vm.requestEdit(account(5, "旧名"))
        vm.updateAccount(5, "新名", 8_000L)
        runCurrent()

        assertEquals("操作失败，请重试", vm.uiState.value.errorMessage)
        assertTrue(repo.renamed.isEmpty())
        assertTrue(repo.initialBalanceUpdates.isEmpty())
        assertEquals(5L, vm.uiState.value.editingAccount?.id)
        assertFalse(vm.uiState.value.busy)
    }

    @Test
    fun `requestDelete computes impact into pendingDelete`() = runTest {
        val repo = FakeAccountRepository().apply {
            impactById = mapOf(5L to AccountDeleteImpact(affectedTransactionCount = 7, affectedTransferCount = 3))
        }
        val vm = viewModel(repo)
        runCurrent()

        val target = account(5, "微信")
        vm.requestDelete(target)
        runCurrent()

        assertEquals(target, vm.uiState.value.pendingDelete?.account)
        assertEquals(7, vm.uiState.value.pendingDelete?.affectedTransactionCount)
        assertEquals(3, vm.uiState.value.pendingDelete?.affectedTransferCount)
        assertFalse(vm.uiState.value.busy)
    }

    @Test
    fun `requestDelete surfaces transfer count for delete confirmation`() = runTest {
        val repo = FakeAccountRepository().apply {
            impactById = mapOf(5L to AccountDeleteImpact(affectedTransactionCount = 0, affectedTransferCount = 2))
        }
        val vm = viewModel(repo)
        runCurrent()

        vm.requestDelete(account(5, "微信"))
        runCurrent()

        assertEquals(0, vm.uiState.value.pendingDelete?.affectedTransactionCount)
        assertEquals(2, vm.uiState.value.pendingDelete?.affectedTransferCount)
    }

    @Test
    fun `confirmDelete deletes clears state and emits Deleted`() = runTest {
        val repo = FakeAccountRepository()
        val vm = viewModel(repo)
        runCurrent()

        val events = mutableListOf<AccountManageEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            vm.events.collect { events.add(it) }
        }

        vm.requestDelete(account(5, "微信"))
        runCurrent()
        vm.confirmDelete()
        runCurrent()

        assertEquals(listOf(5L), repo.deletedIds)
        assertNull(vm.uiState.value.pendingDelete)
        assertEquals(listOf(AccountManageEvent.Deleted), events)
    }

    @Test
    fun `confirmDelete failure emits Failed and clears pendingDelete`() = runTest {
        val repo = FakeAccountRepository().apply { deleteError = RuntimeException("db down") }
        val vm = viewModel(repo)
        runCurrent()

        val events = mutableListOf<AccountManageEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            vm.events.collect { events.add(it) }
        }

        vm.requestDelete(account(5, "微信"))
        runCurrent()
        vm.confirmDelete()
        runCurrent()

        assertEquals(listOf(AccountManageEvent.Failed("删除失败，请重试")), events)
        assertNull(vm.uiState.value.pendingDelete)
        assertFalse(vm.uiState.value.busy)
    }

    @Test
    fun `busy guard prevents concurrent actions`() = runTest {
        val repo = FakeAccountRepository().apply { addGate = CompletableDeferred() }
        val vm = viewModel(repo)
        runCurrent()

        vm.addAccount("微信", 0L)
        runCurrent()
        vm.addAccount("支付宝", 0L)
        vm.updateAccount(1L, "新名", 0L)

        repo.addGate?.complete(Unit)
        runCurrent()

        assertEquals(listOf("微信"), repo.added)
        assertTrue(repo.renamed.isEmpty())
        assertFalse(vm.uiState.value.busy)
    }

    private fun viewModel(repo: AccountRepository = FakeAccountRepository()) = AccountManageViewModel(repo)

    private fun account(id: Long, name: String) = Account(id = id, name = name)

    /** [AccountRepository] 手写 fake：以 MutableStateFlow 驱动观察，记录变更入参并可配置异常/挂起。 */
    private class FakeAccountRepository : AccountRepository {

        val accountsFlow = MutableStateFlow<List<Account>>(emptyList())

        var addError: Throwable? = null
        var addBalanceError: Throwable? = null
        var renameError: Throwable? = null
        var updateBalanceError: Throwable? = null
        var deleteImpactError: Throwable? = null
        var deleteError: Throwable? = null
        var addGate: CompletableDeferred<Unit>? = null
        var deleteGate: CompletableDeferred<Unit>? = null

        val added = mutableListOf<String>()
        val renamed = mutableListOf<Pair<Long, String>>()
        val initialBalanceUpdates = mutableListOf<Pair<Long, Long>>()
        val deletedIds = mutableListOf<Long>()
        var impactById: Map<Long, AccountDeleteImpact> = emptyMap()
        var defaultImpact = AccountDeleteImpact(0)
        var addResultId: Long = 100L

        override fun observeAccounts(): Flow<List<Account>> = accountsFlow

        override suspend fun getAccounts(): List<Account> = accountsFlow.value

        override fun observeBalances(): Flow<List<AccountBalance>> = flowOf(emptyList())

        override suspend fun addAccount(name: String): Long {
            addGate?.await()
            addError?.let { throw it }
            added += name
            return addResultId
        }

        /** 模拟事务原子语义：任一步失败即不提交（不记录任何写）。 */
        override suspend fun addAccount(name: String, initialBalanceCents: Long): Long {
            addGate?.await()
            addError?.let { throw it }
            if (initialBalanceCents != 0L) {
                addBalanceError?.let { throw it }
            }
            added += name
            if (initialBalanceCents != 0L) {
                initialBalanceUpdates += addResultId to initialBalanceCents
            }
            return addResultId
        }

        override suspend fun renameAccount(id: Long, newName: String) {
            renameError?.let { throw it }
            renamed += id to newName
        }

        /** 模拟事务原子语义：任一步失败即不提交（不记录任何写）。 */
        override suspend fun updateAccount(id: Long, newName: String, initialBalanceCents: Long) {
            renameError?.let { throw it }
            updateBalanceError?.let { throw it }
            renamed += id to newName
            initialBalanceUpdates += id to initialBalanceCents
        }

        override suspend fun getDeleteImpact(id: Long): AccountDeleteImpact {
            deleteImpactError?.let { throw it }
            return impactById[id] ?: defaultImpact
        }

        override suspend fun deleteAccount(id: Long) {
            deleteGate?.await()
            deleteError?.let { throw it }
            deletedIds += id
        }

        override fun observeLastUsedAccountId(): Flow<Long?> = flowOf(null)

        override suspend fun saveLastUsedAccountId(id: Long?) = Unit

        override suspend fun updateInitialBalance(accountId: Long, cents: Long) {
            initialBalanceUpdates += accountId to cents
        }
    }
}
