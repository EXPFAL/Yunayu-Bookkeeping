package com.expfal.yunayu.data.repository

import com.expfal.yunayu.data.local.dao.TagDao
import com.expfal.yunayu.data.local.dao.TransactionDao
import com.expfal.yunayu.data.local.entity.TagEntity
import com.expfal.yunayu.data.local.entity.TransactionEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/** [TagDao] 手写 fake：按 parentId 返回预置子节点，记录新增/重命名/删除调用。 */
class FakeTagDao : TagDao {

    var childrenByParent: Map<Long?, List<TagEntity>> = emptyMap()
    var allTags: List<TagEntity> = emptyList()
    var countByNameResult: Int = 0
    val countByNameCalls = mutableListOf<Pair<Long, String>>()
    var nextSortOrderByParent: Map<Long, Int> = emptyMap()
    val insertedTags = mutableListOf<TagEntity>()
    var nextInsertId: Long = 1L
    val renameCalls = mutableListOf<Triple<Long, String, Long>>()
    var renameResult: Int = 1
    val deleteCalls = mutableListOf<Long>()

    override fun observeChildren(parentId: Long?): Flow<List<TagEntity>> =
        flowOf(childrenByParent[parentId] ?: emptyList())

    override suspend fun getChildren(parentId: Long?): List<TagEntity> =
        childrenByParent[parentId] ?: emptyList()

    override suspend fun updateSortOrder(tags: List<TagEntity>) = Unit

    override suspend fun insert(tag: TagEntity): Long {
        insertedTags += tag
        return nextInsertId
    }

    override suspend fun insertAll(tags: List<TagEntity>): List<Long> = emptyList()

    override suspend fun getAll(): List<TagEntity> = allTags

    override suspend fun countByName(parentId: Long, name: String): Int {
        countByNameCalls += parentId to name
        return countByNameResult
    }

    override suspend fun nextSortOrder(parentId: Long): Int = nextSortOrderByParent[parentId] ?: 0

    override suspend fun renameById(tagId: Long, name: String, updatedAt: Long): Int {
        renameCalls += Triple(tagId, name, updatedAt)
        return renameResult
    }

    override suspend fun deleteById(tagId: Long) {
        deleteCalls += tagId
    }
}

/** [TransactionDao] 手写 fake：记录 insert/聚合查询入参、观察流简单实现。 */
class FakeTransactionDao : TransactionDao {

    val inserted = mutableListOf<TransactionEntity>()
    var nextInsertId: Long = 1L
    var recentTagRows: List<TransactionDao.RecentTagRow> = emptyList()
    val recentFrequentTagsCalls = mutableListOf<Pair<Long, Int>>()
    var expenseSumFlow: Flow<Long> = flowOf(0L)
    val expenseSumCalls = mutableListOf<Pair<Long, Long>>()
    var heldCentsFlow: Flow<Long> = flowOf(0L)
    var windowTotalsResult: TransactionDao.WindowTotalsRow = TransactionDao.WindowTotalsRow(0L, 0L)
    val windowTotalsCalls = mutableListOf<Pair<Long, Long>>()
    var categoryExpenseRows: List<TransactionDao.CategoryExpenseRow> = emptyList()
    val categoryExpenseCalls = mutableListOf<Pair<Long, Long>>()
    var recentRowsFlow: Flow<List<TransactionDao.RecentTransactionRow>> = flowOf(emptyList())
    val recentCalls = mutableListOf<Int>()
    var countByTagIdsResult: Int = 0
    val countByTagIdsCalls = mutableListOf<List<Long>>()
    val deletedByIdCalls = mutableListOf<Long>()
    var filteredRowsFlow: Flow<List<TransactionDao.RecentTransactionRow>> = flowOf(emptyList())
    val filteredCalls = mutableListOf<Triple<Long?, Long?, String?>>()
    var filteredByTagsRowsFlow: Flow<List<TransactionDao.RecentTransactionRow>> = flowOf(emptyList())
    val filteredByTagsCalls = mutableListOf<Pair<Triple<Long?, Long?, String?>, List<Long>>>()

    override suspend fun insert(transaction: TransactionEntity): Long {
        inserted += transaction
        return nextInsertId
    }

    override suspend fun delete(transaction: TransactionEntity) = Unit

    override suspend fun deleteById(id: Long) {
        deletedByIdCalls += id
    }

    override fun observeAll(): Flow<List<TransactionEntity>> = flowOf(emptyList())

    override fun observeByTag(tagId: Long): Flow<List<TransactionEntity>> = flowOf(emptyList())

    override suspend fun getRecentFrequentTags(
        sinceEpochMillis: Long,
        limit: Int,
    ): List<TransactionDao.RecentTagRow> {
        recentFrequentTagsCalls += sinceEpochMillis to limit
        return recentTagRows
    }

    override fun observeExpenseSumBetween(
        startInclusiveMs: Long,
        endExclusiveMs: Long,
    ): Flow<Long> {
        expenseSumCalls += startInclusiveMs to endExclusiveMs
        return expenseSumFlow
    }

    override fun observeHeldCents(): Flow<Long> = heldCentsFlow

    override suspend fun getWindowTotals(
        startInclusiveMs: Long,
        endExclusiveMs: Long,
    ): TransactionDao.WindowTotalsRow {
        windowTotalsCalls += startInclusiveMs to endExclusiveMs
        return windowTotalsResult
    }

    override suspend fun getExpenseByCategory(
        startInclusiveMs: Long,
        endExclusiveMs: Long,
    ): List<TransactionDao.CategoryExpenseRow> {
        categoryExpenseCalls += startInclusiveMs to endExclusiveMs
        return categoryExpenseRows
    }

    override fun observeRecent(limit: Int): Flow<List<TransactionDao.RecentTransactionRow>> {
        recentCalls += limit
        return recentRowsFlow
    }

    override fun observeFiltered(
        startInclusiveMs: Long?,
        endExclusiveMs: Long?,
        noteKeyword: String?,
    ): Flow<List<TransactionDao.RecentTransactionRow>> {
        filteredCalls += Triple(startInclusiveMs, endExclusiveMs, noteKeyword)
        return filteredRowsFlow
    }

    override fun observeFilteredByTags(
        startInclusiveMs: Long?,
        endExclusiveMs: Long?,
        noteKeyword: String?,
        tagIds: List<Long>,
    ): Flow<List<TransactionDao.RecentTransactionRow>> {
        filteredByTagsCalls += Triple(startInclusiveMs, endExclusiveMs, noteKeyword) to tagIds
        return filteredByTagsRowsFlow
    }

    override suspend fun countByTagIds(tagIds: List<Long>): Int {
        countByTagIdsCalls += tagIds
        return countByTagIdsResult
    }
}
