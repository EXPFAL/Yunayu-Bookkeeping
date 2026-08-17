package com.expfal.yunayu.data.repository

import android.content.Context
import android.content.ContextWrapper
import android.database.sqlite.SQLiteConstraintException
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.room.DatabaseConfiguration
import androidx.room.InvalidationTracker
import androidx.sqlite.db.SupportSQLiteOpenHelper
import com.expfal.yunayu.data.local.YunayuDatabase
import com.expfal.yunayu.data.local.dao.AccountDao
import com.expfal.yunayu.data.local.dao.ReportDao
import com.expfal.yunayu.data.local.dao.TagDao
import com.expfal.yunayu.data.local.dao.TransactionDao
import com.expfal.yunayu.data.local.dao.TransferDao
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
    fun `addAccountAtomic inserts then writes initial balance in one unit`() = runTest {
        val dao = FakeAccountDao().apply { nextInsertId = 42L }
        val repository = repository(dao, FakeTransactionDao())

        val id = repository.addAccountAtomic("微信", 12_345L)

        assertEquals(42L, id)
        assertEquals(1, dao.insertedAccounts.size)
        assertEquals(listOf(42L to 12_345L), dao.updateInitialBalanceCalls)
    }

    @Test
    fun `addAccountAtomic skips initial balance write when zero`() = runTest {
        val dao = FakeAccountDao()
        val repository = repository(dao, FakeTransactionDao())

        repository.addAccountAtomic("微信", 0L)

        assertEquals(1, dao.insertedAccounts.size)
        assertTrue(dao.updateInitialBalanceCalls.isEmpty())
    }

    @Test
    fun `addAccountAtomic propagates balance write failure`() = runTest {
        // 期初写入抛异常时事务体向外传播，由外层 withTransaction 回滚第一步的 insert
        val dao = FakeAccountDao().apply { updateInitialBalanceError = RuntimeException("db down") }
        val repository = repository(dao, FakeTransactionDao())

        val error = captureError { repository.addAccountAtomic("微信", 12_345L) }

        assertEquals(RuntimeException::class.java, error?.javaClass)
    }

    @Test
    fun `updateAccountAtomic renames then writes initial balance in one unit`() = runTest {
        val dao = FakeAccountDao().apply { allAccounts = listOf(accountEntity(2L, "微信")) }
        val repository = repository(dao, FakeTransactionDao())

        repository.updateAccountAtomic(2L, "建行", 8_000L)

        assertEquals(listOf(2L to "建行"), dao.renameCalls)
        assertEquals(listOf(2L to 8_000L), dao.updateInitialBalanceCalls)
    }

    @Test
    fun `updateAccountAtomic propagates balance write failure`() = runTest {
        // 期初写入抛异常时事务体向外传播，由外层 withTransaction 回滚第一步的 rename
        val dao = FakeAccountDao().apply {
            allAccounts = listOf(accountEntity(2L, "微信"))
            updateInitialBalanceError = RuntimeException("db down")
        }
        val repository = repository(dao, FakeTransactionDao())

        val error = captureError { repository.updateAccountAtomic(2L, "建行", 8_000L) }

        assertEquals(RuntimeException::class.java, error?.javaClass)
    }

    @Test
    fun `getDeleteImpact delegates countByAccountId for transactions and transfers`() = runTest {
        val transactionDao = FakeTransactionDao().apply { countByAccountIdResult = 7 }
        val transferDao = FakeTransferDao().apply { countByAccountIdResult = 3 }
        val repository = repository(FakeAccountDao(), transactionDao, transferDao)

        val impact = repository.getDeleteImpact(2L)

        assertEquals(AccountDeleteImpact(affectedTransactionCount = 7, affectedTransferCount = 3), impact)
        assertEquals(listOf(2L), transactionDao.countByAccountIdCalls)
        assertEquals(listOf(2L), transferDao.countByAccountIdCalls)
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
            observeAllFlow = flowOf(
                listOf(
                    accountEntity(1L, "微信", 100L, initialBalanceCents = 5_000L),
                    accountEntity(2L, "支付宝", 200L),
                ),
            )
        }
        val repository = repository(dao, FakeTransactionDao())

        val accounts = repository.observeAccounts().first()

        assertEquals(
            listOf(
                Account(id = 1L, name = "微信", createdAt = 100L, initialBalanceCents = 5_000L),
                Account(id = 2L, name = "支付宝", createdAt = 200L),
            ),
            accounts,
        )
    }

    @Test
    fun `observeBalances aggregates initial balance transaction net and transfer net`() = runTest {
        val accountDao = FakeAccountDao().apply {
            observeAllFlow = flowOf(
                listOf(
                    accountEntity(1L, "微信", 100L, initialBalanceCents = 5_000L),
                    accountEntity(2L, "支付宝", 200L),
                ),
            )
        }
        val transactionDao = FakeTransactionDao().apply {
            balancesByAccountFlow = flowOf(
                listOf(
                    TransactionDao.HeldByAccountRow(1L, "微信", 7_000L),
                    TransactionDao.HeldByAccountRow(2L, "支付宝", 5_000L),
                    TransactionDao.HeldByAccountRow(null, null, -2_000L),
                ),
            )
        }
        val transferDao = FakeTransferDao().apply {
            netByAccountFlow = flowOf(
                listOf(
                    TransferDao.TransferNetRow(1L, -1_000L),
                    TransferDao.TransferNetRow(2L, 1_000L),
                ),
            )
        }
        val repository = repository(accountDao, transactionDao, transferDao)

        val balances = repository.observeBalances().first()

        assertEquals(
            listOf(
                AccountBalance(1L, "微信", 11_000L),
                AccountBalance(2L, "支付宝", 6_000L),
                AccountBalance(null, null, -2_000L),
            ),
            balances,
        )
    }

    @Test
    fun `observeBalances invariant total equals initial sum plus transaction net`() = runTest {
        // 期初总和 = 5000 + 0 = 5000；交易净额 = 7000 + 5000 - 2000 = 10000；转账净额合计 0
        val accountDao = FakeAccountDao().apply {
            observeAllFlow = flowOf(
                listOf(
                    accountEntity(1L, "微信", 100L, initialBalanceCents = 5_000L),
                    accountEntity(2L, "支付宝", 200L),
                ),
            )
        }
        val transactionDao = FakeTransactionDao().apply {
            balancesByAccountFlow = flowOf(
                listOf(
                    TransactionDao.HeldByAccountRow(1L, "微信", 7_000L),
                    TransactionDao.HeldByAccountRow(2L, "支付宝", 5_000L),
                    TransactionDao.HeldByAccountRow(null, null, -2_000L),
                ),
            )
        }
        val transferDao = FakeTransferDao().apply {
            netByAccountFlow = flowOf(
                listOf(
                    TransferDao.TransferNetRow(1L, -1_000L),
                    TransferDao.TransferNetRow(2L, 1_000L),
                ),
            )
        }
        val repository = repository(accountDao, transactionDao, transferDao)

        val balances = repository.observeBalances().first()

        // 总资金 = Σ账户余额 + 未指定净额 = 期初总和 + 累计净结余；转账净额合计 0 不改变总额
        assertEquals(15_000L, balances.sumOf { it.balanceCents })
        assertEquals(5_000L + 10_000L, balances.sumOf { it.balanceCents })
    }

    @Test
    fun `updateInitialBalance delegates to dao`() = runTest {
        val dao = FakeAccountDao()
        val repository = repository(dao, FakeTransactionDao())

        repository.updateInitialBalance(2L, 12_345L)

        assertEquals(listOf(2L to 12_345L), dao.updateInitialBalanceCalls)
    }

    @Test
    fun `observeBalances excludes deleted account so initial balance does not linger`() = runTest {
        // 账户 2（期初 0）已删除，观察流只剩账户 1（期初 5000）
        val accountDao = FakeAccountDao().apply {
            observeAllFlow = flowOf(
                listOf(accountEntity(1L, "微信", 100L, initialBalanceCents = 5_000L)),
            )
        }
        val transactionDao = FakeTransactionDao().apply {
            balancesByAccountFlow = flowOf(
                listOf(TransactionDao.HeldByAccountRow(1L, "微信", 7_000L)),
            )
        }
        val repository = repository(accountDao, transactionDao)

        val balances = repository.observeBalances().first()

        assertEquals(listOf(AccountBalance(1L, "微信", 12_000L)), balances)
        // 已删除账户的期初/交易不残留，总额只含剩余账户
        assertEquals(12_000L, balances.sumOf { it.balanceCents })
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

    private fun repository(
        accountDao: FakeAccountDao,
        transactionDao: FakeTransactionDao,
        transferDao: FakeTransferDao = FakeTransferDao(),
    ): AccountRepositoryImpl = AccountRepositoryImpl(fakeDatabase(), accountDao, transactionDao, transferDao, nullContext)

    /** 构造阶段占位：非事务路径不触碰数据库，事务路径经 [AccountRepositoryImpl] 的 internal 事务体直测。 */
    private fun fakeDatabase(): YunayuDatabase = object : YunayuDatabase() {
        override fun accountDao(): AccountDao = throw UnsupportedOperationException()
        override fun tagDao(): TagDao = throw UnsupportedOperationException()
        override fun transactionDao(): TransactionDao = throw UnsupportedOperationException()
        override fun transferDao(): TransferDao = throw UnsupportedOperationException()
        override fun reportDao(): ReportDao = throw UnsupportedOperationException()
        override fun createOpenHelper(config: DatabaseConfiguration): SupportSQLiteOpenHelper =
            throw UnsupportedOperationException()
        override fun createInvalidationTracker(): InvalidationTracker = throw UnsupportedOperationException()
        override fun clearAllTables() = Unit
    }

    private fun accountEntity(
        id: Long,
        name: String,
        createdAt: Long = 100L,
        initialBalanceCents: Long = 0L,
    ) = AccountEntity(id = id, name = name, createdAt = createdAt, initialBalanceCents = initialBalanceCents)

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
