package com.expfal.yunayu.domain.repository

import com.expfal.yunayu.domain.model.AccountFilter
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

    /** 按主键一次性查询交易；不存在返回 `null`。 */
    suspend fun getById(id: Long): Transaction?

    /**
     * 更新一笔交易（按主键整行覆盖金额 / 类型 / 备注 / 标签 / 账户），
     * 保留原 `occurredAt` 与 `createdAt`（编辑不改变发生时间与创建时间）。
     */
    suspend fun updateTransaction(transaction: Transaction)

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
     * 观察持有资金（分）：口径为「期初余额总和 + 累计收入 − 累计支出」（全历史净结余），
     * 转账在账户间守恒、不改变总额；无账户与交易时发射 `0`。
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
     * 观察按时间窗、账户、标签集合与备注关键字过滤的交易摘要（含标签名），按发生时间倒序。
     *
     * `accountFilter` 控制账户维度过滤（全部 / 仅未指定 / 指定账户）；`tagIds` 为空表示不按标签过滤；
     * `startInclusiveMs` / `endExclusiveMs` 为 null 表示不设对应边界；`noteKeyword` 为 null 或空白表示不按
     * 备注过滤。备注关键字的通配符（`%`/`_`/`\`）转义由实现层负责。
     */
    fun observeFiltered(
        startInclusiveMs: Long?,
        endExclusiveMs: Long?,
        tagIds: List<Long>,
        noteKeyword: String?,
        accountFilter: AccountFilter,
    ): Flow<List<RecentTransaction>>

    /** 观察未分类交易数（未挂任何标签），供整理功能入口展示。 */
    fun observeUncategorizedCount(): Flow<Int>

    /** 一次性获取未分类交易摘要（含标签名），按发生时间倒序。 */
    suspend fun getUncategorized(): List<RecentTransaction>

    /**
     * 批量把交易挂到标签：key = tagId、value = 该标签下的交易 id 列表，单事务应用。
     * 空映射为无操作，不做 DAO 调用。
     */
    suspend fun assignTags(assignments: Map<Long, List<Long>>)

    /**
     * 一次性获取挂在一组标签下的交易的 `occurredAt` 列表（供标签合并前的报告标脏）。
     *
     * 仅返回发生时刻，不携带交易其它字段；[tagIds] 为空返回空列表。
     */
    suspend fun getOccurredAtsByTagIds(tagIds: List<Long>): List<Long>
}
