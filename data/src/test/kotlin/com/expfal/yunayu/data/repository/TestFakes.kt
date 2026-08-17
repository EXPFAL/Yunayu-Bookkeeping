package com.expfal.yunayu.data.repository

import com.expfal.yunayu.data.local.dao.AccountDao
import com.expfal.yunayu.data.local.dao.TagDao
import com.expfal.yunayu.data.local.dao.TransactionDao
import com.expfal.yunayu.data.local.dao.TransferDao
import com.expfal.yunayu.data.local.entity.AccountEntity
import com.expfal.yunayu.data.local.entity.TagEntity
import com.expfal.yunayu.data.local.entity.TransactionEntity
import com.expfal.yunayu.data.local.entity.TransferEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/** [AccountDao] 手写 fake：记录插入/重命名/删除调用，可注入唯一约束异常。 */
class FakeAccountDao : AccountDao {

    var allAccounts: List<AccountEntity> = emptyList()
    var observeAllFlow: Flow<List<AccountEntity>> = flowOf(emptyList())
    val insertedAccounts = mutableListOf<AccountEntity>()
    var nextInsertId: Long = 1L
    var insertError: Throwable? = null
    var countByNameResult: Int = 0
    val countByNameCalls = mutableListOf<String>()
    val renameCalls = mutableListOf<Pair<Long, String>>()
    var renameResult: Int = 1
    val deleteCalls = mutableListOf<Long>()
    var initialBalanceSumFlow: Flow<Long> = flowOf(0L)
    val updateInitialBalanceCalls = mutableListOf<Pair<Long, Long>>()
    var updateInitialBalanceError: Throwable? = null

    override fun observeAll(): Flow<List<AccountEntity>> = observeAllFlow

    override suspend fun getAll(): List<AccountEntity> = allAccounts

    override suspend fun insert(account: AccountEntity): Long {
        insertError?.let { throw it }
        insertedAccounts += account
        return nextInsertId
    }

    override suspend fun countByName(name: String): Int {
        countByNameCalls += name
        return countByNameResult
    }

    override suspend fun renameById(id: Long, name: String): Int {
        renameCalls += id to name
        return renameResult
    }

    override suspend fun deleteById(id: Long) {
        deleteCalls += id
    }

    override suspend fun updateInitialBalance(id: Long, cents: Long) {
        updateInitialBalanceError?.let { throw it }
        updateInitialBalanceCalls += id to cents
    }

    override fun observeInitialBalanceSum(): Flow<Long> = initialBalanceSumFlow
}

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
    val recentFrequentTagsCalls = mutableListOf<Triple<Long, String, Int>>()
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
    val filteredCalls = mutableListOf<FilterCall>()
    var filteredByTagsRowsFlow: Flow<List<TransactionDao.RecentTransactionRow>> = flowOf(emptyList())
    val filteredByTagsCalls = mutableListOf<FilterByTagsCall>()

    override suspend fun insert(transaction: TransactionEntity): Long {
        inserted += transaction
        return nextInsertId
    }

    var entityById: Map<Long, TransactionEntity> = emptyMap()
    val updated = mutableListOf<TransactionEntity>()

    override suspend fun update(entity: TransactionEntity) {
        updated += entity
    }

    override suspend fun delete(transaction: TransactionEntity) = Unit

    override suspend fun deleteById(id: Long) {
        deletedByIdCalls += id
    }

    override suspend fun getById(id: Long): TransactionEntity? = entityById[id]

    override fun observeAll(): Flow<List<TransactionEntity>> = flowOf(emptyList())

    override fun observeByTag(tagId: Long): Flow<List<TransactionEntity>> = flowOf(emptyList())

    override suspend fun getRecentFrequentTags(
        sinceEpochMillis: Long,
        type: String,
        limit: Int,
    ): List<TransactionDao.RecentTagRow> {
        recentFrequentTagsCalls += Triple(sinceEpochMillis, type, limit)
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
        accountMode: Int,
        accountId: Long?,
    ): Flow<List<TransactionDao.RecentTransactionRow>> {
        filteredCalls += FilterCall(startInclusiveMs, endExclusiveMs, noteKeyword, accountMode, accountId)
        return filteredRowsFlow
    }

    override fun observeFilteredByTags(
        startInclusiveMs: Long?,
        endExclusiveMs: Long?,
        noteKeyword: String?,
        tagIds: List<Long>,
        accountMode: Int,
        accountId: Long?,
    ): Flow<List<TransactionDao.RecentTransactionRow>> {
        filteredByTagsCalls += FilterByTagsCall(startInclusiveMs, endExclusiveMs, noteKeyword, tagIds, accountMode, accountId)
        return filteredByTagsRowsFlow
    }

    override suspend fun countByTagIds(tagIds: List<Long>): Int {
        countByTagIdsCalls += tagIds
        return countByTagIdsResult
    }

    var uncategorizedCountFlow: Flow<Int> = flowOf(0)
    var uncategorizedRows: List<TransactionDao.RecentTransactionRow> = emptyList()
    val updateTagIdsCalls = mutableListOf<Pair<List<Long>, Long>>()
    val applyTagAssignmentsCalls = mutableListOf<Map<Long, List<Long>>>()

    override fun observeUncategorizedCount(): Flow<Int> = uncategorizedCountFlow

    override suspend fun getUncategorizedSnapshot(): List<TransactionDao.RecentTransactionRow> =
        uncategorizedRows

    override suspend fun updateTagIds(ids: List<Long>, tagId: Long) {
        updateTagIdsCalls += ids to tagId
    }

    override suspend fun applyTagAssignments(assignments: Map<Long, List<Long>>) {
        applyTagAssignmentsCalls += assignments
    }

    val updateTagIdByTagIdsCalls = mutableListOf<Pair<List<Long>, Long>>()
    var updateTagIdByTagIdsResult: Int = 0
    val occurredAtsByTagIdsCalls = mutableListOf<List<Long>>()
    var occurredAtsByTagIdsResult: List<Long> = emptyList()

    override suspend fun updateTagIdByTagIds(sourceTagIds: List<Long>, targetTagId: Long): Int {
        updateTagIdByTagIdsCalls += sourceTagIds to targetTagId
        return updateTagIdByTagIdsResult
    }

    override suspend fun getOccurredAtsByTagIds(tagIds: List<Long>): List<Long> {
        occurredAtsByTagIdsCalls += tagIds
        return occurredAtsByTagIdsResult
    }

    var balancesByAccountFlow: Flow<List<TransactionDao.HeldByAccountRow>> = flowOf(emptyList())
    var countByAccountIdResult: Int = 0
    val countByAccountIdCalls = mutableListOf<Long>()

    override fun observeBalancesByAccount(): Flow<List<TransactionDao.HeldByAccountRow>> =
        balancesByAccountFlow

    override suspend fun countByAccountId(accountId: Long): Int {
        countByAccountIdCalls += accountId
        return countByAccountIdResult
    }
}

/** [TransferDao] 手写 fake：记录插入/删除调用，观察流与净额聚合流可注入。 */
class FakeTransferDao : TransferDao {

    var observeAllFlow: Flow<List<TransferEntity>> = flowOf(emptyList())
    val inserted = mutableListOf<TransferEntity>()
    var nextInsertId: Long = 1L
    val deletedByIdCalls = mutableListOf<Long>()
    var netByAccountFlow: Flow<List<TransferDao.TransferNetRow>> = flowOf(emptyList())
    var countByAccountIdResult: Int = 0
    val countByAccountIdCalls = mutableListOf<Long>()

    override fun observeAll(): Flow<List<TransferEntity>> = observeAllFlow

    override suspend fun insert(transfer: TransferEntity): Long {
        inserted += transfer
        return nextInsertId
    }

    override suspend fun deleteById(id: Long) {
        deletedByIdCalls += id
    }

    override suspend fun countByAccountId(accountId: Long): Int {
        countByAccountIdCalls += accountId
        return countByAccountIdResult
    }

    override fun observeNetByAccount(): Flow<List<TransferDao.TransferNetRow>> = netByAccountFlow
}

/** [TagMergeExecutor] 手写 fake：记录 merge 调用，可配置异常。 */
class FakeTagMergeExecutor : TagMergeExecutor {

    val mergeCalls = mutableListOf<Pair<Long, Long>>()
    var mergeError: Throwable? = null

    override suspend fun merge(keepTagId: Long, dropTagId: Long) {
        mergeError?.let { throw it }
        mergeCalls += keepTagId to dropTagId
    }
}

/** 一次 [TransactionDao.observeFiltered]（无标签）调用的入参快照。 */
data class FilterCall(
    val startInclusiveMs: Long?,
    val endExclusiveMs: Long?,
    val noteKeyword: String?,
    val accountMode: Int,
    val accountId: Long?,
)

/** 一次 [TransactionDao.observeFilteredByTags] 调用的入参快照。 */
data class FilterByTagsCall(
    val startInclusiveMs: Long?,
    val endExclusiveMs: Long?,
    val noteKeyword: String?,
    val tagIds: List<Long>,
    val accountMode: Int,
    val accountId: Long?,
)
