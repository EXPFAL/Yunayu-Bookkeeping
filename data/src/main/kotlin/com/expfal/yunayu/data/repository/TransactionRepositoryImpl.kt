package com.expfal.yunayu.data.repository

import com.expfal.yunayu.data.local.dao.TransactionDao
import com.expfal.yunayu.data.local.entity.TransactionEntity
import com.expfal.yunayu.domain.model.Transaction
import com.expfal.yunayu.domain.model.TransactionType
import com.expfal.yunayu.domain.repository.TransactionRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** [TransactionRepository] 的 Room 实现。 */
@Singleton
class TransactionRepositoryImpl @Inject constructor(
    private val transactionDao: TransactionDao,
) : TransactionRepository {

    override suspend fun add(transaction: Transaction): Long =
        transactionDao.insert(transaction.toEntity())

    override fun observeAll(): Flow<List<Transaction>> =
        transactionDao.observeAll().map { entities -> entities.map { it.toDomain() } }

    override fun observeByTag(tagId: Long): Flow<List<Transaction>> =
        transactionDao.observeByTag(tagId).map { entities -> entities.map { it.toDomain() } }

    private fun Transaction.toEntity(): TransactionEntity = TransactionEntity(
        id = id,
        amountCents = amountCents,
        type = type.name,
        note = note,
        tagId = tagId,
        occurredAt = occurredAt,
        createdAt = System.currentTimeMillis(),
    )

    private fun TransactionEntity.toDomain(): Transaction = Transaction(
        id = id,
        amountCents = amountCents,
        type = runCatching { TransactionType.valueOf(type) }.getOrDefault(TransactionType.EXPENSE),
        note = note,
        tagId = tagId,
        occurredAt = occurredAt,
    )
}
