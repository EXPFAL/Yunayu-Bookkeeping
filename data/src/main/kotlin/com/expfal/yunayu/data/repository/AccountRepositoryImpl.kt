package com.expfal.yunayu.data.repository

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import android.util.Log
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.expfal.yunayu.data.local.dao.AccountDao
import com.expfal.yunayu.data.local.dao.TransactionDao
import com.expfal.yunayu.data.local.entity.AccountEntity
import com.expfal.yunayu.domain.model.Account
import com.expfal.yunayu.domain.model.AccountBalance
import com.expfal.yunayu.domain.model.AccountDeleteImpact
import com.expfal.yunayu.domain.model.DuplicateAccountNameException
import com.expfal.yunayu.domain.repository.AccountRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
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
    private val accountDao: AccountDao,
    private val transactionDao: TransactionDao,
    @ApplicationContext private val context: Context,
) : AccountRepository {

    override fun observeAccounts(): Flow<List<Account>> =
        accountDao.observeAll().map { entities -> entities.map { it.toDomain() } }

    override suspend fun getAccounts(): List<Account> =
        accountDao.getAll().map { it.toDomain() }

    override fun observeBalances(): Flow<List<AccountBalance>> =
        transactionDao.observeBalancesByAccount().map { rows ->
            rows.map { AccountBalance(accountId = it.accountId, accountName = it.accountName, balanceCents = it.balanceCents) }
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

    override suspend fun getDeleteImpact(id: Long): AccountDeleteImpact =
        AccountDeleteImpact(affectedTransactionCount = transactionDao.countByAccountId(id))

    override suspend fun deleteAccount(id: Long) {
        accountDao.deleteById(id)
    }

    override fun observeLastUsedAccountId(): Flow<Long?> =
        context.accountDataStore.data.map { it.toLastUsedAccountId() }

    override suspend fun saveLastUsedAccountId(id: Long?) {
        context.accountDataStore.edit { it.writeLastUsedAccountId(id) }
    }

    private fun AccountEntity.toDomain(): Account = Account(id = id, name = name, createdAt = createdAt)

    private companion object {
        const val TAG = "AccountRepositoryImpl"
    }
}
