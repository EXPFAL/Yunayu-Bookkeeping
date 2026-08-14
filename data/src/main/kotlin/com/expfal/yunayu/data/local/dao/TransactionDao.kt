package com.expfal.yunayu.data.local.dao

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.Query
import com.expfal.yunayu.data.local.entity.TagEntity
import com.expfal.yunayu.data.local.entity.TransactionEntity
import kotlinx.coroutines.flow.Flow

/** 交易基础 DAO。 */
@Dao
interface TransactionDao {

    /** 最近常用标签聚合行：标签实体 + 使用次数。 */
    data class RecentTagRow(
        @Embedded val tag: TagEntity,
        @ColumnInfo(name = "usage_count") val usageCount: Long,
    )

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

    /**
     * 统计起始时间之后各标签的使用频次，按频次降序、最近使用时间降序返回前 [limit] 个。
     */
    @Query(
        "SELECT tags.*, COUNT(transactions.id) AS usage_count " +
            "FROM transactions INNER JOIN tags ON tags.id = transactions.tag_id " +
            "WHERE transactions.occurred_at >= :sinceEpochMillis " +
            "GROUP BY tags.id " +
            "ORDER BY usage_count DESC, MAX(transactions.occurred_at) DESC " +
            "LIMIT :limit",
    )
    suspend fun getRecentFrequentTags(sinceEpochMillis: Long, limit: Int): List<RecentTagRow>
}
