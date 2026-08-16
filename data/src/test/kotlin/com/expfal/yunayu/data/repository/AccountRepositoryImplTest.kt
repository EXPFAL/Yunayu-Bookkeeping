package com.expfal.yunayu.data.repository

import android.content.Context
import android.content.ContextWrapper
import android.database.sqlite.SQLiteConstraintException
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import com.expfal.yunayu.data.local.dao.TransactionDao
import com.expfal.yunayu.data.local.entity.AccountEntity
import com.expfal.yunayu.domain.model.Account
import com.expfal.yunayu.domain.model.AccountBalance
import com.expfal.yunayu.domain.model.AccountDeleteImpact
import com.expfal.yunayu.domain.model.DuplicateAccountNameException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

/**
 * [AccountRepositoryImpl] 的 JVM 单元测试（手写 fake DAO + coroutines-test）。
 *
 * 账户 CRUD 与映射经 fake DAO 直测；账户记忆的 DataStore 实例经 `preferencesDataStore` 绑定到应用
 * Context，JVM 单测无法直接注入，故按 [NlApiConfigRepositoryImplTest] 先例用
 * [PreferenceDataStoreFactory.create] 以临时文件直测内部函数 `toLastUsedAccountId` /
 * `writeLastUsedAccountId` 的读写与 null 语义。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AccountRepositoryImplTest {

    @TempDir
    lateinit var tempDir: File

    @Test
    fun `addAccount inserts trimmed name and returns id`() = runTest {
        val dao = FakeAccountDao().apply { nextInsertId = 42L }
        val repository = repository(dao, FakeTransactionDao())

        val id = repository.addAccount("  微信  ")

        assertEquals(42L, id)
        assertEquals(listOf("微信"), dao.countByNameCalls)
        assertEquals(1, dao.insertedAccounts.size)
        val inserted = dao.insertedAccounts.single()
        assertEquals("微信", inserted.name)
        assertTrue(inserted.createdAt > 0L)
    }

    @Test
    fun `addAccount throws DuplicateAccountNameException on duplicate name`() = runTest {
        val dao = FakeAccountDao().apply { countByNameResult = 1 }
        val repository = repository(dao, FakeTransactionDao())

        val error = captureError { repository.addAccount("微信") }

        assertEquals(DuplicateAccountNameException::class.java, error?.javaClass)
    }

    @Test
    fun `addAccount rejects blank name`() = runTest {
        val repository = repository(FakeAccountDao(), FakeTransactionDao())

        val error = captureError { repository.addAccount("   ") }

        assertEquals(IllegalArgumentException::class.java, error?.javaClass)
        assertEquals("账户名不可为空", error?.message)
    }

    @Test
    fun `addAccount falls back to DuplicateAccountNameException on unique constraint`() = runTest {
        val dao = FakeAccountDao().apply { insertError = UniqueConstraintException() }
        val repository = repository(dao, FakeTransactionDao())

        val error = captureError { repository.addAccount("微信") }

        assertEquals(DuplicateAccountNameException::class.java, error?.javaClass)
    }

    @Test
    fun `renameAccount trims and delegates rename`() = runTest {
        val dao = FakeAccountDao().apply {
            allAccounts = listOf(accountEntity(2L, "微信"))
        }
        val repository = repository(dao, FakeTransactionDao())

        repository.renameAccount(2L, "  建行  ")

        assertEquals(listOf(2L to "建行"), dao.renameCalls)
    }

    @Test
    fun `renameAccount throws DuplicateAccountNameException on duplicate`() = runTest {
        val dao = FakeAccountDao().apply {
            allAccounts = listOf(accountEntity(2L, "微信"), accountEntity(3L, "支付宝"))
        }
        val repository = repository(dao, FakeTransactionDao())

        val error = captureError { repository.renameAccount(3L, "微信") }

        assertEquals(DuplicateAccountNameException::class.java, error?.javaClass)
    }

    @Test
    fun `renameAccount throws when target missing`() = runTest {
        val repository = repository(FakeAccountDao(), FakeTransactionDao())

        val error = captureError { repository.renameAccount(999L, "建行") }

        assertEquals(IllegalArgumentException::class.java, error?.javaClass)
        assertEquals("账户不存在：999", error?.message)
    }

    @Test
    fun `renameAccount rejects blank name`() = runTest {
        val repository = repository(FakeAccountDao(), FakeTransactionDao())

        val error = captureError { repository.renameAccount(2L, "  ") }

        assertEquals(IllegalArgumentException::class.java, error?.javaClass)
        assertEquals("账户名不可为空", error?.message)
    }

    @Test
    fun `getDeleteImpact delegates countByAccountId`() = runTest {
        val transactionDao = FakeTransactionDao().apply { countByAccountIdResult = 7 }
        val repository = repository(FakeAccountDao(), transactionDao)

        val impact = repository.getDeleteImpact(2L)

        assertEquals(AccountDeleteImpact(7), impact)
        assertEquals(listOf(2L), transactionDao.countByAccountIdCalls)
    }

    @Test
    fun `deleteAccount delegates deleteById`() = runTest {
        val dao = FakeAccountDao()
        val repository = repository(dao, FakeTransactionDao())

        repository.deleteAccount(2L)

        assertEquals(listOf(2L), dao.deleteCalls)
    }

    @Test
    fun `observeAccounts maps entities to domain models`() = runTest {
        val dao = FakeAccountDao().apply {
            observeAllFlow = flowOf(listOf(accountEntity(1L, "微信", 100L), accountEntity(2L, "支付宝", 200L)))
        }
        val repository = repository(dao, FakeTransactionDao())

        val accounts = repository.observeAccounts().first()

        assertEquals(
            listOf(Account(id = 1L, name = "微信", createdAt = 100L), Account(id = 2L, name = "支付宝", createdAt = 200L)),
            accounts,
        )
    }

    @Test
    fun `observeBalances maps rows to AccountBalance including unspecified`() = runTest {
        val transactionDao = FakeTransactionDao().apply {
            balancesByAccountFlow = flowOf(
                listOf(
                    TransactionDao.HeldByAccountRow(1L, "微信", 7_000L),
                    TransactionDao.HeldByAccountRow(null, null, -2_000L),
                ),
            )
        }
        val repository = repository(FakeAccountDao(), transactionDao)

        val balances = repository.observeBalances().first()

        assertEquals(
            listOf(AccountBalance(1L, "微信", 7_000L), AccountBalance(null, null, -2_000L)),
            balances,
        )
    }

    @Test
    fun `last used account id is null when never saved`() = runTest {
        val dataStore = newDataStore(backgroundScope, "empty.preferences_pb")

        assertNull(dataStore.data.map { it.toLastUsedAccountId() }.first())
    }

    @Test
    fun `saves and reads last used account id`() = runTest {
        val dataStore = newDataStore(backgroundScope, "save.preferences_pb")

        dataStore.edit { it.writeLastUsedAccountId(42L) }

        assertEquals(42L, dataStore.data.map { it.toLastUsedAccountId() }.first())
    }

    @Test
    fun `save null removes last used account id key`() = runTest {
        val dataStore = newDataStore(backgroundScope, "remove.preferences_pb")

        dataStore.edit { it.writeLastUsedAccountId(42L) }
        dataStore.edit { it.writeLastUsedAccountId(null) }

        assertNull(dataStore.data.map { it.toLastUsedAccountId() }.first())
    }

    private fun repository(accountDao: FakeAccountDao, transactionDao: FakeTransactionDao): AccountRepositoryImpl =
        AccountRepositoryImpl(accountDao, transactionDao, nullContext)

    private fun accountEntity(id: Long, name: String, createdAt: Long = 100L) =
        AccountEntity(id = id, name = name, createdAt = createdAt)

    private fun newDataStore(scope: CoroutineScope, fileName: String) = PreferenceDataStoreFactory.create(
        scope = scope,
        produceFile = { File(tempDir, fileName) },
    )

    private suspend fun captureError(block: suspend () -> Unit): Throwable? =
        try {
            block()
            null
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Throwable) {
            e
        }

    private companion object {
        // 账户 CRUD/映射测试不触碰 DataStore，故用一个不委托真实 base 的 ContextWrapper 满足构造签名
        // （DataStore 仅 observeLastUsedAccountId / saveLastUsedAccountId 用到，二者另经内部函数直测）。
        val nullContext: Context = ContextWrapper(null)
    }

    /** 模拟 accounts.name 唯一约束冲突（mockable jar 构造器不保存 message，故覆写）。 */
    private class UniqueConstraintException : SQLiteConstraintException() {
        override val message: String? = "UNIQUE constraint failed: accounts.name"
    }
}
