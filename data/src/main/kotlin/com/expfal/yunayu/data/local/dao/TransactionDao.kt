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

    /** 最近交易行：交易实体 + 左连接标签名与图标。 */
    data class RecentTransactionRow(
        @Embedded val transaction: TransactionEntity,
        @ColumnInfo(name = "tag_name") val tagName: String?,
        @ColumnInfo(name = "tag_icon") val tagIcon: String?,
    )

    /** 时间窗收支聚合行：单次查询的窗口收入 / 支出总额（分）。 */
    data class WindowTotalsRow(
        @ColumnInfo(name = "income_cents") val incomeCents: Long,
        @ColumnInfo(name = "expense_cents") val expenseCents: Long,
    )

    /** 时间窗分类支出聚合行：标签名（未分类为 null）与支出总额（分）。 */
    data class CategoryExpenseRow(
        @ColumnInfo(name = "tag_name") val tagName: String?,
        @ColumnInfo(name = "cents") val cents: Long,
    )

    @Insert
    suspend fun insert(transaction: TransactionEntity): Long

    @Delete
    suspend fun delete(transaction: TransactionEntity)

    /** 按主键删除一笔交易。 */
    @Query("DELETE FROM transactions WHERE id = :id")
    suspend fun deleteById(id: Long)

    /** 观察全部交易，按发生时间倒序。 */
    @Query("SELECT * FROM transactions ORDER BY occurred_at DESC")
    fun observeAll(): Flow<List<TransactionEntity>>

    /** 观察挂在指定学业标签下的交易，按发生时间倒序。 */
    @Query("SELECT * FROM transactions WHERE tag_id = :tagId ORDER BY occurred_at DESC")
    fun observeByTag(tagId: Long): Flow<List<TransactionEntity>>

    /**
     * 统计起始时间之后、指定收支类型下各标签的使用频次，按频次降序、最近使用时间降序返回前 [limit] 个。
     * [type] 为收支方向过滤（参数绑定），复用 `(occurred_at, type)` 复合索引。
     */
    @Query(
        "SELECT tags.*, COUNT(transactions.id) AS usage_count " +
            "FROM transactions INNER JOIN tags ON tags.id = transactions.tag_id " +
            "WHERE transactions.occurred_at >= :sinceEpochMillis " +
            "AND transactions.type = :type " +
            "GROUP BY tags.id " +
            "ORDER BY usage_count DESC, MAX(transactions.occurred_at) DESC " +
            "LIMIT :limit",
    )
    suspend fun getRecentFrequentTags(sinceEpochMillis: Long, type: String, limit: Int): List<RecentTagRow>

    /** 观察时间窗内的支出总额（分），无匹配行时返回 0。 */
    @Query(
        "SELECT COALESCE(SUM(amount_cents), 0) FROM transactions " +
            "WHERE occurred_at >= :startInclusiveMs AND occurred_at < :endExclusiveMs " +
            "AND type = 'EXPENSE'",
    )
    fun observeExpenseSumBetween(startInclusiveMs: Long, endExclusiveMs: Long): Flow<Long>

    /** 观察持有资金（分）= 累计收入 − 累计支出（全历史），无交易时返回 0。 */
    @Query(
        "SELECT COALESCE(SUM(CASE WHEN type = 'INCOME' THEN amount_cents ELSE -amount_cents END), 0) " +
            "FROM transactions",
    )
    fun observeHeldCents(): Flow<Long>

    /** 单次查询时间窗内收入 / 支出总额（分），无匹配行时两项均为 0。 */
    @Query(
        "SELECT " +
            "COALESCE(SUM(CASE WHEN type = 'INCOME' THEN amount_cents ELSE 0 END), 0) AS income_cents, " +
            "COALESCE(SUM(CASE WHEN type = 'EXPENSE' THEN amount_cents ELSE 0 END), 0) AS expense_cents " +
            "FROM transactions " +
            "WHERE occurred_at >= :startInclusiveMs AND occurred_at < :endExclusiveMs",
    )
    suspend fun getWindowTotals(startInclusiveMs: Long, endExclusiveMs: Long): WindowTotalsRow

    /** 单次查询时间窗内支出按标签分组（未分类归入 null），按金额降序。 */
    @Query(
        "SELECT tag.name AS tag_name, SUM(t.amount_cents) AS cents " +
            "FROM transactions t LEFT JOIN tags tag ON tag.id = t.tag_id " +
            "WHERE t.type = 'EXPENSE' " +
            "AND t.occurred_at >= :startInclusiveMs AND t.occurred_at < :endExclusiveMs " +
            "GROUP BY t.tag_id ORDER BY cents DESC",
    )
    suspend fun getExpenseByCategory(
        startInclusiveMs: Long,
        endExclusiveMs: Long,
    ): List<CategoryExpenseRow>

    /** 观察最近 [limit] 笔交易（含标签名与图标），按发生时间倒序。 */
    @Query(
        "SELECT t.*, tag.name AS tag_name, tag.icon AS tag_icon " +
            "FROM transactions t LEFT JOIN tags tag ON tag.id = t.tag_id " +
            "ORDER BY t.occurred_at DESC, t.id DESC LIMIT :limit",
    )
    fun observeRecent(limit: Int): Flow<List<RecentTransactionRow>>

    /**
     * 观察满足时间窗与备注关键字过滤的交易（含标签名与图标，不做标签过滤），按发生时间倒序。
     * 时间参数为 null 表示不设对应边界；关键字为 null 表示不按备注过滤。
     */
    @Query(
        "SELECT t.*, tag.name AS tag_name, tag.icon AS tag_icon " +
            "FROM transactions t LEFT JOIN tags tag ON tag.id = t.tag_id " +
            "WHERE (:startInclusiveMs IS NULL OR t.occurred_at >= :startInclusiveMs) " +
            "AND (:endExclusiveMs IS NULL OR t.occurred_at < :endExclusiveMs) " +
            "AND (:noteKeyword IS NULL OR t.note LIKE '%' || :noteKeyword || '%' ESCAPE '\\') " +
            "ORDER BY t.occurred_at DESC, t.id DESC",
    )
    fun observeFiltered(
        startInclusiveMs: Long?,
        endExclusiveMs: Long?,
        noteKeyword: String?,
    ): Flow<List<RecentTransactionRow>>

    /**
     * 观察满足时间窗、备注关键字与标签集合过滤的交易（含标签名与图标），按发生时间倒序。
     * 时间参数为 null 表示不设对应边界；关键字为 null 表示不按备注过滤。
     */
    @Query(
        "SELECT t.*, tag.name AS tag_name, tag.icon AS tag_icon " +
            "FROM transactions t LEFT JOIN tags tag ON tag.id = t.tag_id " +
            "WHERE (:startInclusiveMs IS NULL OR t.occurred_at >= :startInclusiveMs) " +
            "AND (:endExclusiveMs IS NULL OR t.occurred_at < :endExclusiveMs) " +
            "AND (:noteKeyword IS NULL OR t.note LIKE '%' || :noteKeyword || '%' ESCAPE '\\') " +
            "AND t.tag_id IN (:tagIds) " +
            "ORDER BY t.occurred_at DESC, t.id DESC",
    )
    fun observeFilteredByTags(
        startInclusiveMs: Long?,
        endExclusiveMs: Long?,
        noteKeyword: String?,
        tagIds: List<Long>,
    ): Flow<List<RecentTransactionRow>>

    /** 统计挂在一组标签下的交易数（删除影响面提示）。 */
    @Query("SELECT COUNT(*) FROM transactions WHERE tag_id IN (:tagIds)")
    suspend fun countByTagIds(tagIds: List<Long>): Int
}
