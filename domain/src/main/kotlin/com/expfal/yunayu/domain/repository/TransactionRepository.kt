package com.expfal.yunayu.domain.repository

import com.expfal.yunayu.domain.model.CategoryExpense
import com.expfal.yunayu.domain.model.RecentTransaction
import com.expfal.yunayu.domain.model.Transaction
import com.expfal.yunayu.domain.model.WindowTotals
import kotlinx.coroutines.flow.Flow

/** 交易仓储接口，由 :data 模块实现。 */
interface TransactionRepository {

    /** 新增一笔交易，返回其主键。 */
    suspend fun add(transaction: Transaction): Long

    /** 删除一笔交易（按主键）。 */
    suspend fun delete(transactionId: Long)

    /** 观察全部交易，按发生时间倒序。 */
    fun observeAll(): Flow<List<Transaction>>

    /** 观察挂在指定学业标签下的交易，按发生时间倒序。 */
    fun observeByTag(tagId: Long): Flow<List<Transaction>>

    /**
     * 观察时间窗内的支出总额（分）：`[startInclusiveMs, endExclusiveMs)`，仅统计支出，
     * 无匹配交易时发射 `0`。
     */
    fun observeExpenseSumBetween(startInclusiveMs: Long, endExclusiveMs: Long): Flow<Long>

    /** 观察最近 [limit] 笔交易摘要（含标签名），按发生时间倒序。 */
    fun observeRecent(limit: Int): Flow<List<RecentTransaction>>

    /**
     * 观察持有资金（分）：口径为「累计收入 − 累计支出」（全历史净结余），
     * 无任何交易时发射 `0`。
     */
    fun observeHeldCents(): Flow<Long>

    /**
     * 单次查询时间窗内的收支总额（分）：`[startInclusiveMs, endExclusiveMs)`，
     * 无匹配交易时收入与支出均为 `0`。
     */
    suspend fun getWindowTotals(startInclusiveMs: Long, endExclusiveMs: Long): WindowTotals

    /**
     * 单次查询时间窗内的支出按分类聚合（含未分类，`tagName = null`），
     * 按支出金额降序返回。
     */
    suspend fun getExpenseByCategory(
        startInclusiveMs: Long,
        endExclusiveMs: Long,
    ): List<CategoryExpense>

    /**
     * 观察按时间窗、标签集合与备注关键字过滤的交易摘要（含标签名），按发生时间倒序。
     *
     * `tagIds` 为空表示不按标签过滤；`startInclusiveMs` / `endExclusiveMs` 为 null 表示不设对应边界；
     * `noteKeyword` 为 null 或空白表示不按备注过滤。备注关键字的通配符（`%`/`_`/`\`）转义由实现层负责。
     */
    fun observeFiltered(
        startInclusiveMs: Long?,
        endExclusiveMs: Long?,
        tagIds: List<Long>,
        noteKeyword: String?,
    ): Flow<List<RecentTransaction>>
}
