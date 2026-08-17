package com.expfal.yunayu.data.repository

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import android.util.Log
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.withTransaction
import com.expfal.yunayu.data.local.YunayuDatabase
import com.expfal.yunayu.data.local.dao.AccountDao
import com.expfal.yunayu.data.local.dao.TransactionDao
import com.expfal.yunayu.data.local.dao.TransferDao
import com.expfal.yunayu.data.local.entity.AccountEntity
import com.expfal.yunayu.domain.model.Account
import com.expfal.yunayu.domain.model.AccountBalance
import com.expfal.yunayu.domain.model.AccountDeleteImpact
import com.expfal.yunayu.domain.model.DuplicateAccountNameException
import com.expfal.yunayu.domain.repository.AccountRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** 账户记忆 DataStore 实例（单例，按名称去重）。 */
private val Context.accountDataStore by preferencesDataStore(name = "account_prefs")

/** 上次使用账户 id 的 DataStore 键。 */
internal val LAST_USED_ACCOUNT_ID_KEY = longPreferencesKey("last_used_account_id")

/** 从 [Preferences] 还原上次使用账户 id；缺省（从未选择）为 null。 */
internal fun Preferences.toLastUsedAccountId(): Long? = this[LAST_USED_ACCOUNT_ID_KEY]

/** 把上次使用账户 id 写入 [MutablePreferences]；`null` 删除键（等价于未选择）。 */
internal fun MutablePreferences.writeLastUsedAccountId(id: Long?) {
    if (id == null) {
        remove(LAST_USED_ACCOUNT_ID_KEY)
    } else {
        this[LAST_USED_ACCOUNT_ID_KEY] = id
    }
}

/** [AccountRepository] 的 Room + DataStore 实现。 */
@Singleton
class AccountRepositoryImpl @Inject constructor(
    private val database: YunayuDatabase,
    private val accountDao: AccountDao,
    private val transactionDao: TransactionDao,
    private val transferDao: TransferDao,
    @ApplicationContext private val context: Context,
) : AccountRepository {

    override fun observeAccounts(): Flow<List<Account>> =
        accountDao.observeAll().map { entities -> entities.map { it.toDomain() } }

    override suspend fun getAccounts(): List<Account> =
        accountDao.getAll().map { it.toDomain() }

    /**
     * 观察按账户分组的余额：账户余额 = 期初余额 + 交易净额 + 转账净额；
     * 「未指定账户」分组（accountId/accountName 均为 null）无期初与转账，仅含交易净额。
     */
    override fun observeBalances(): Flow<List<AccountBalance>> = combine(
        accountDao.observeAll(),
        transactionDao.observeBalancesByAccount(),
        transferDao.observeNetByAccount(),
    ) { accounts, transactionRows, transferRows ->
        val transactionNetById = transactionRows.associate { it.accountId to it.balanceCents }
        val transferNetById = transferRows.associate { it.accountId to it.netCents }
        val namedBalances = accounts.map { account ->
            AccountBalance(
                accountId = account.id,
                accountName = account.name,
                balanceCents = account.initialBalanceCents +
                    (transactionNetById[account.id] ?: 0L) +
                    (transferNetById[account.id] ?: 0L),
            )
        }
        val unspecified = transactionRows.firstOrNull { it.accountId == null }?.let {
            AccountBalance(accountId = null, accountName = null, balanceCents = it.balanceCents)
        }
        namedBalances + listOfNotNull(unspecified)
    }

    override suspend fun addAccount(name: String): Long {
        val trimmed = name.trim()
        require(trimmed.isNotEmpty()) { "账户名不可为空" }
        return try {
            if (accountDao.countByName(trimmed) > 0) {
                throw DuplicateAccountNameException("账户「$trimmed」已存在")
            }
            val now = System.currentTimeMillis()
            accountDao.insert(AccountEntity(id = 0L, name = trimmed, createdAt = now))
        } catch (e: SQLiteConstraintException) {
            Log.w(TAG, "Constraint violation while adding account", e)
            if (e.message?.contains("UNIQUE") == true) {
                throw DuplicateAccountNameException("账户「$trimmed」已存在")
            }
            throw e
        }
    }

    /**
     * 原子新增账户并写入期初余额（分）：复用 [addAccount] 建户，期初非 0 时在同一事务内追加
     * 写期初，任一步失败整体回滚。
     */
    override suspend fun addAccount(name: String, initialBalanceCents: Long): Long =
        database.withTransaction { addAccountAtomic(name, initialBalanceCents) }

    /** [addAccount] 的事务体：建户 + 条件写期初，供 [addAccount] 事务包裹与单测直测。 */
    internal suspend fun addAccountAtomic(name: String, initialBalanceCents: Long): Long {
        val id = addAccount(name)
        if (initialBalanceCents != 0L) {
            accountDao.updateInitialBalance(id, initialBalanceCents)
        }
        return id
    }

    override suspend fun renameAccount(id: Long, newName: String) {
        val trimmed = newName.trim()
        require(trimmed.isNotEmpty()) { "账户名不可为空" }
        val all = accountDao.getAll()
        val target = all.firstOrNull { it.id == id }
            ?: throw IllegalArgumentException("账户不存在：$id")
        if (all.any { it.id != id && it.name == trimmed }) {
            throw DuplicateAccountNameException("账户「$trimmed」已存在")
        }
        val affected = accountDao.renameById(id, trimmed)
        if (affected == 0) throw IllegalArgumentException("账户不存在：$id")
    }

    /**
     * 原子更新账户名与期初余额（分）：复用 [renameAccount] 改名后在同一事务内写期初，
     * 任一步失败整体回滚。
     */
    override suspend fun updateAccount(id: Long, newName: String, initialBalanceCents: Long) =
        database.withTransaction { updateAccountAtomic(id, newName, initialBalanceCents) }

    /** [updateAccount] 的事务体：改名 + 写期初，供 [updateAccount] 事务包裹与单测直测。 */
    internal suspend fun updateAccountAtomic(id: Long, newName: String, initialBalanceCents: Long) {
        renameAccount(id, newName)
        accountDao.updateInitialBalance(id, initialBalanceCents)
    }

    override suspend fun getDeleteImpact(id: Long): AccountDeleteImpact =
        AccountDeleteImpact(
            affectedTransactionCount = transactionDao.countByAccountId(id),
            affectedTransferCount = transferDao.countByAccountId(id),
        )

    override suspend fun deleteAccount(id: Long) {
        accountDao.deleteById(id)
    }

    override suspend fun updateInitialBalance(accountId: Long, cents: Long) {
        accountDao.updateInitialBalance(accountId, cents)
    }

    override fun observeLastUsedAccountId(): Flow<Long?> =
        context.accountDataStore.data.map { it.toLastUsedAccountId() }

    override suspend fun saveLastUsedAccountId(id: Long?) {
        context.accountDataStore.edit { it.writeLastUsedAccountId(id) }
    }

    private fun AccountEntity.toDomain(): Account = Account(
        id = id,
        name = name,
        createdAt = createdAt,
        initialBalanceCents = initialBalanceCents,
    )

    private companion object {
        const val TAG = "AccountRepositoryImpl"
    }
}
