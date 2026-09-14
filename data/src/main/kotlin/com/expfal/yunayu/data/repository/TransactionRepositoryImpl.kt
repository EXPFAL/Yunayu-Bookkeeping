package com.expfal.yunayu.data.repository

import android.util.Log
import com.expfal.yunayu.data.local.dao.AccountDao
import com.expfal.yunayu.data.local.dao.TransactionDao
import com.expfal.yunayu.data.local.entity.TransactionEntity
import com.expfal.yunayu.domain.model.AccountFilter
import com.expfal.yunayu.domain.model.CategoryExpense
import com.expfal.yunayu.domain.model.RecentTransaction
import com.expfal.yunayu.domain.model.Transaction
import com.expfal.yunayu.domain.model.TransactionType
import com.expfal.yunayu.domain.model.WindowTotals
import com.expfal.yunayu.domain.repository.TransactionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** [TransactionRepository] 的 Room 实现。 */
@Singleton
class TransactionRepositoryImpl @Inject constructor(
    private val transactionDao: TransactionDao,
    private val accountDao: AccountDao,
) : TransactionRepository {

    override suspend fun add(transaction: Transaction): Long =
        transactionDao.insert(transaction.toEntity())

    override suspend fun delete(transactionId: Long) {
        transactionDao.deleteById(transactionId)
    }

    override suspend fun getById(id: Long): Transaction? {
        val entity = transactionDao.getById(id) ?: return null
        return entity.toDomainOrNull()
    }

    override suspend fun updateTransaction(transaction: Transaction) {
        val existing = transactionDao.getById(transaction.id) ?: return
        transactionDao.update(transaction.toEntity(existing.createdAt))
    }

    override fun observeAll(): Flow<List<Transaction>> =
        transactionDao.observeAll().map { entities -> entities.mapNotNull { it.toDomainOrNull() } }

    override fun observeByTag(tagId: Long): Flow<List<Transaction>> =
        transactionDao.observeByTag(tagId).map { entities -> entities.mapNotNull { it.toDomainOrNull() } }

    override fun observeExpenseSumBetween(
        startInclusiveMs: Long,
        endExclusiveMs: Long,
    ): Flow<Long> = transactionDao.observeExpenseSumBetween(startInclusiveMs, endExclusiveMs)

    override fun observeHeldCents(): Flow<Long> = combine(
        transactionDao.observeHeldCents(),
        accountDao.observeInitialBalanceSum(),
    ) { netCents, initialBalanceSum -> netCents + initialBalanceSum }

    override suspend fun getWindowTotals(
        startInclusiveMs: Long,
        endExclusiveMs: Long,
    ): WindowTotals = transactionDao.getWindowTotals(startInclusiveMs, endExclusiveMs).let {
        WindowTotals(incomeCents = it.incomeCents, expenseCents = it.expenseCents)
    }

    override suspend fun getExpenseByCategory(
        startInclusiveMs: Long,
        endExclusiveMs: Long,
    ): List<CategoryExpense> = transactionDao.getExpenseByCategory(startInclusiveMs, endExclusiveMs)
        .map { CategoryExpense(tagName = it.tagName, cents = it.cents) }

    override fun observeRecent(limit: Int): Flow<List<RecentTransaction>> =
        transactionDao.observeRecent(limit)
            .map { rows -> rows.mapNotNull { it.toRecentDomainOrNull() } }
            .distinctUntilChanged()

    override fun observeFiltered(
        startInclusiveMs: Long?,
        endExclusiveMs: Long?,
        tagIds: List<Long>,
        noteKeyword: String?,
        accountFilter: AccountFilter,
    ): Flow<List<RecentTransaction>> {
        val keyword = noteKeyword?.takeIf { it.isNotBlank() }?.let { escapeLikeKeyword(it) }
        val (mode, id) = accountFilter.toModeAndId()
        val rows: Flow<List<TransactionDao.RecentTransactionRow>> =
            if (tagIds.isEmpty()) {
                transactionDao.observeFiltered(startInclusiveMs, endExclusiveMs, keyword, mode, id)
            } else {
                transactionDao.observeFilteredByTags(startInclusiveMs, endExclusiveMs, keyword, tagIds, mode, id)
            }
        return rows.map { list -> list.mapNotNull { it.toRecentDomainOrNull() } }
            .distinctUntilChanged()
    }

    private fun TransactionDao.RecentTransactionRow.toRecentDomainOrNull(): RecentTransaction? {
        val type = parseTransactionType(transaction.type, transaction.id) ?: return null
        return RecentTransaction(
            id = transaction.id,
            amountCents = transaction.amountCents,
            type = type,
            tagName = tagName,
            occurredAt = transaction.occurredAt,
            note = transaction.note,
            accountName = accountName,
        )
    }

    override fun observeUncategorizedCount(): Flow<Int> =
        transactionDao.observeUncategorizedCount()

    override suspend fun getUncategorized(): List<RecentTransaction> =
        transactionDao.getUncategorizedSnapshot().mapNotNull { it.toRecentDomainOrNull() }

    override suspend fun assignTags(assignments: Map<Long, List<Long>>) {
        if (assignments.isEmpty()) return
        transactionDao.applyTagAssignments(assignments)
    }

    override suspend fun getOccurredAtsByTagIds(tagIds: List<Long>): List<Long> =
        if (tagIds.isEmpty()) emptyList() else transactionDao.getOccurredAtsByTagIds(tagIds)

    private fun Transaction.toEntity(createdAt: Long = System.currentTimeMillis()): TransactionEntity = TransactionEntity(
        id = id,
        amountCents = amountCents,
        type = type.name,
        note = note,
        tagId = tagId,
        accountId = accountId,
        occurredAt = occurredAt,
        createdAt = createdAt,
    )

    private fun TransactionEntity.toDomainOrNull(): Transaction? {
        val parsedType = parseTransactionType(type, id) ?: return null
        return Transaction(
            id = id,
            amountCents = amountCents,
            type = parsedType,
            note = note,
            tagId = tagId,
            accountId = accountId,
            occurredAt = occurredAt,
        )
    }

    private fun parseTransactionType(raw: String, id: Long): TransactionType? =
        runCatching { TransactionType.valueOf(raw) }
            .onFailure { Log.e(TAG, "Unknown transaction type \"$raw\" for id=$id, skipping row") }
            .getOrNull()

    private companion object {
        const val TAG = "TransactionRepo"

        /**
         * 转义 LIKE 关键字中的特殊字符，使关键字按字面量匹配。
         *
         * 顺序敏感：先转义反斜杠 `\` → `\\`，再转义 `%` → `\%`、`_` → `\_`，
         * 避免后续转义产物中的反斜杠被二次处理。
         */
        fun escapeLikeKeyword(keyword: String): String =
            keyword.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
    }
}

/** 把账户筛选三态映射为 DAO 的 `(accountMode, accountId)` 入参。 */
private fun AccountFilter.toModeAndId(): Pair<Int, Long?> = when (this) {
    AccountFilter.All -> TransactionDao.ACCOUNT_MODE_ALL to null
    AccountFilter.Unspecified -> TransactionDao.ACCOUNT_MODE_UNSPECIFIED to null
    is AccountFilter.Specific -> TransactionDao.ACCOUNT_MODE_SPECIFIC to accountId
}
