package com.expfal.yunayu.data.local.dao

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.expfal.yunayu.data.local.entity.TransferEntity
import kotlinx.coroutines.flow.Flow

/** 转账基础 DAO。 */
@Dao
interface TransferDao {

    /** 按账户聚合的转账净额行：账户 id 与净额（分）= 转入 − 转出。 */
    data class TransferNetRow(
        @ColumnInfo(name = "account_id") val accountId: Long,
        @ColumnInfo(name = "net_cents") val netCents: Long,
    )

    /** 观察全部转账，按发生时间倒序。 */
    @Query("SELECT * FROM transfers ORDER BY occurred_at DESC")
    fun observeAll(): Flow<List<TransferEntity>>

    @Insert
    suspend fun insert(transfer: TransferEntity): Long

    /** 按主键删除一笔转账。 */
    @Query("DELETE FROM transfers WHERE id = :id")
    suspend fun deleteById(id: Long)

    /** 统计一个账户作为 from 或 to 涉及的转账数（账户删除影响面提示）。 */
    @Query("SELECT COUNT(*) FROM transfers WHERE from_account_id = :accountId OR to_account_id = :accountId")
    suspend fun countByAccountId(accountId: Long): Int

    /**
     * 观察按账户聚合的转账净额（分）：转出记负、转入记正，净额 = 转入 − 转出。
     * 供账户余额聚合（账户余额 = 期初 + 交易净额 + 转账净额）使用。
     */
    @Query(
        "SELECT account_id, COALESCE(SUM(net), 0) AS net_cents FROM (" +
            "SELECT from_account_id AS account_id, -amount_cents AS net FROM transfers " +
            "UNION ALL " +
            "SELECT to_account_id AS account_id, amount_cents AS net FROM transfers" +
            ") GROUP BY account_id",
    )
    fun observeNetByAccount(): Flow<List<TransferNetRow>>
}
