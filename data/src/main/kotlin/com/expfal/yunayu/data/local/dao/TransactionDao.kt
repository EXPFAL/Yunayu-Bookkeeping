package com.expfal.yunayu.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import com.expfal.yunayu.data.local.entity.TransactionEntity
import kotlinx.coroutines.flow.Flow

/** 交易基础 DAO。 */
@Dao
interface TransactionDao {

    @Insert
    suspend fun insert(transaction: TransactionEntity): Long

    @Delete
    suspend fun delete(transaction: TransactionEntity)

    /** 观察全部交易，按发生时间倒序。 */
    @Query("SELECT * FROM transactions ORDER BY occurred_at DESC")
    fun observeAll(): Flow<List<TransactionEntity>>

    /** 观察挂在指定学业标签下的交易，按发生时间倒序。 */
    @Query("SELECT * FROM transactions WHERE tag_id = :tagId ORDER BY occurred_at DESC")
    fun observeByTag(tagId: Long): Flow<List<TransactionEntity>>
}
