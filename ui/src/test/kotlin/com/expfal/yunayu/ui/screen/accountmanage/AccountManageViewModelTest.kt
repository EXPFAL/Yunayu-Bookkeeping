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

        vm.addAccount("微信")
        runCurrent()

        assertEquals(listOf("微信"), repo.added)
        assertEquals(listOf(AccountManageEvent.Added), events)
        assertNull(vm.uiState.value.errorMessage)
        assertFalse(vm.uiState.value.busy)
    }

    @Test
    fun `addAccount duplicate name maps friendly error without crash`() = runTest {
        val repo = FakeAccountRepository().apply { addError = DuplicateAccountNameException("dup") }
        val vm = viewModel(repo)
        runCurrent()

        vm.addAccount("微信")
        runCurrent()

        assertEquals("同名账户已存在", vm.uiState.value.errorMessage)
        assertFalse(vm.uiState.value.busy)
    }

    @Test
    fun `addAccount illegal argument maps repository message`() = runTest {
        val repo = FakeAccountRepository().apply { addError = IllegalArgumentException("账户名不可为空") }
        val vm = viewModel(repo)
        runCurrent()

        vm.addAccount(" ")
        runCurrent()

        assertEquals("账户名不可为空", vm.uiState.value.errorMessage)
    }

    @Test
    fun `rename success emits Renamed and closes rename state`() = runTest {
        val repo = FakeAccountRepository()
        val vm = viewModel(repo)
        runCurrent()

        val events = mutableListOf<AccountManageEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            vm.events.collect { events.add(it) }
        }

        val target = account(5, "旧名")
        vm.requestRename(target)
        vm.rename(5, "新名")
        runCurrent()

        assertEquals(listOf(5L to "新名"), repo.renamed)
        assertEquals(listOf(AccountManageEvent.Renamed), events)
        assertNull(vm.uiState.value.renamingAccount)
    }

    @Test
    fun `rename duplicate name keeps rename state with inline error`() = runTest {
        val repo = FakeAccountRepository().apply { renameError = DuplicateAccountNameException("dup") }
        val vm = viewModel(repo)
        runCurrent()

        vm.requestRename(account(5, "旧名"))
        vm.rename(5, "微信")
        runCurrent()

        assertEquals("同名账户已存在", vm.uiState.value.errorMessage)
        assertEquals(5L, vm.uiState.value.renamingAccount?.id)
    }

    @Test
    fun `requestDelete computes impact into pendingDelete`() = runTest {
        val repo = FakeAccountRepository().apply {
            impactById = mapOf(5L to AccountDeleteImpact(7))
        }
        val vm = viewModel(repo)
        runCurrent()

        val target = account(5, "微信")
        vm.requestDelete(target)
        runCurrent()

        assertEquals(target, vm.uiState.value.pendingDelete?.account)
        assertEquals(7, vm.uiState.value.pendingDelete?.affectedTransactionCount)
        assertFalse(vm.uiState.value.busy)
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

        vm.addAccount("微信")
        runCurrent()
        vm.addAccount("支付宝")
        vm.rename(1L, "新名")

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
        var renameError: Throwable? = null
        var deleteImpactError: Throwable? = null
        var deleteError: Throwable? = null
        var addGate: CompletableDeferred<Unit>? = null
        var deleteGate: CompletableDeferred<Unit>? = null

        val added = mutableListOf<String>()
        val renamed = mutableListOf<Pair<Long, String>>()
        val deletedIds = mutableListOf<Long>()
        var impactById: Map<Long, AccountDeleteImpact> = emptyMap()
        var defaultImpact = AccountDeleteImpact(0)

        override fun observeAccounts(): Flow<List<Account>> = accountsFlow

        override suspend fun getAccounts(): List<Account> = accountsFlow.value

        override fun observeBalances(): Flow<List<AccountBalance>> = flowOf(emptyList())

        override suspend fun addAccount(name: String): Long {
            addGate?.await()
            addError?.let { throw it }
            added += name
            return 100L
        }

        override suspend fun renameAccount(id: Long, newName: String) {
            renameError?.let { throw it }
            renamed += id to newName
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
    }
}
