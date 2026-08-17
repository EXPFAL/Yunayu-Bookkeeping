package com.expfal.yunayu.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.expfal.yunayu.data.local.entity.AccountEntity
import kotlinx.coroutines.flow.Flow

/** 账户 DAO（账户体系迭代）。 */
@Dao
interface AccountDao {

    /** 观察全部账户，按创建时间升序（同刻按 id 升序，保证种子顺序稳定）。 */
    @Query("SELECT * FROM accounts ORDER BY created_at ASC, id ASC")
    fun observeAll(): Flow<List<AccountEntity>>

    /** 一次性获取全部账户（启动补齐幂等校验等场景使用）。 */
    @Query("SELECT * FROM accounts ORDER BY created_at ASC, id ASC")
    suspend fun getAll(): List<AccountEntity>

    @Insert
    suspend fun insert(account: AccountEntity): Long

    /** 统计同名账户数量（重名校验）。 */
    @Query("SELECT COUNT(*) FROM accounts WHERE name = :name")
    suspend fun countByName(name: String): Int

    /** 按 id 重命名账户，返回受影响行数（0 表示目标不存在）。 */
    @Query("UPDATE accounts SET name = :name WHERE id = :id")
    suspend fun renameById(id: Long, name: String): Int

    /** 按 id 删除账户（交易置空由外键 ON DELETE SET NULL 执行，转账由 CASCADE 级联删除）。 */
    @Query("DELETE FROM accounts WHERE id = :id")
    suspend fun deleteById(id: Long)

    /** 按 id 更新账户期初余额（分）。 */
    @Query("UPDATE accounts SET initial_balance_cents = :cents WHERE id = :id")
    suspend fun updateInitialBalance(id: Long, cents: Long)

    /** 观察全部账户期初余额之和（分），无账户时返回 0。 */
    @Query("SELECT COALESCE(SUM(initial_balance_cents), 0) FROM accounts")
    fun observeInitialBalanceSum(): Flow<Long>
}
