package com.expfal.yunayu.domain.usecase

import com.expfal.yunayu.domain.model.Account
import com.expfal.yunayu.domain.model.AccountBalance
import com.expfal.yunayu.domain.model.AccountDeleteImpact
import com.expfal.yunayu.domain.model.AccountPresets
import com.expfal.yunayu.domain.model.DuplicateAccountNameException
import com.expfal.yunayu.domain.repository.AccountRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** [EnsureAccountsUseCase] 的 JVM 单元测试（手写 fake 仓储 + coroutines-test）。 */
class EnsureAccountsUseCaseTest {

    @Test
    fun `creates all preset accounts on first run`() = runTest {
        val repository = FakeAccountRepository()
        val useCase = EnsureAccountsUseCase(repository)

        val result = useCase()

        assertEquals(AccountPresets.PRESET_NAMES, result.created)
        assertTrue(result.skipped.isEmpty())
        assertEquals(AccountPresets.PRESET_NAMES, repository.addedNames)
    }

    @Test
    fun `is idempotent when all presets exist`() = runTest {
        val repository = FakeAccountRepository().apply {
            AccountPresets.PRESET_NAMES.forEach { addExisting(it) }
        }
        val useCase = EnsureAccountsUseCase(repository)

        val result = useCase()

        assertTrue(result.created.isEmpty())
        assertTrue(result.skipped.isEmpty())
        assertTrue(repository.addedNames.isEmpty())
    }

    @Test
    fun `creates only missing accounts on partial seed`() = runTest {
        val repository = FakeAccountRepository().apply { addExisting("微信") }
        val useCase = EnsureAccountsUseCase(repository)

        val result = useCase()

        assertEquals(AccountPresets.PRESET_NAMES - "微信", result.created)
        assertTrue(result.skipped.isEmpty())
        assertEquals(AccountPresets.PRESET_NAMES - "微信", repository.addedNames)
    }

    @Test
    fun `counts duplicate race as skipped instead of failing`() = runTest {
        val repository = FakeAccountRepository().apply { duplicateOnAdd = true }
        val useCase = EnsureAccountsUseCase(repository)

        val result = useCase()

        assertTrue(result.created.isEmpty())
        assertEquals(AccountPresets.PRESET_NAMES, result.skipped)
    }

    /** [AccountRepository] 手写 fake：维护账户名单，记录新增并可注入竞态重名异常。 */
    private class FakeAccountRepository : AccountRepository {

        private val accounts = mutableListOf<Account>()
        val addedNames = mutableListOf<String>()
        var duplicateOnAdd = false
        private var nextId = 1L

        fun addExisting(name: String) {
            accounts += Account(id = nextId++, name = name, createdAt = 100L)
        }

        override fun observeAccounts(): Flow<List<Account>> = flowOf(accounts.toList())

        override suspend fun getAccounts(): List<Account> = accounts.toList()

        override fun observeBalances(): Flow<List<AccountBalance>> = flowOf(emptyList())

        override suspend fun addAccount(name: String): Long {
            if (duplicateOnAdd) throw DuplicateAccountNameException("dup")
            val account = Account(id = nextId++, name = name, createdAt = 100L)
            accounts += account
            addedNames += name
            return account.id
        }

        override suspend fun renameAccount(id: Long, newName: String) = Unit

        override suspend fun getDeleteImpact(id: Long): AccountDeleteImpact = AccountDeleteImpact(0)

        override suspend fun deleteAccount(id: Long) = Unit

        override fun observeLastUsedAccountId(): Flow<Long?> = flowOf(null)

        override suspend fun saveLastUsedAccountId(id: Long?) = Unit
    }
}
