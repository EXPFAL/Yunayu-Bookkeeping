package com.expfal.yunayu.data.repository

import com.expfal.yunayu.data.local.dao.TransferDao
import com.expfal.yunayu.data.local.entity.TransferEntity
import com.expfal.yunayu.domain.model.Transfer
import com.expfal.yunayu.domain.repository.TransferRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** [TransferRepository] 的 Room 实现。 */
@Singleton
class TransferRepositoryImpl @Inject constructor(
    private val transferDao: TransferDao,
) : TransferRepository {

    override fun observeTransfers(): Flow<List<Transfer>> =
        transferDao.observeAll().map { entities -> entities.map { it.toDomain() } }

    override suspend fun insertTransfer(transfer: Transfer): Long =
        transferDao.insert(transfer.toEntity())

    override suspend fun deleteById(id: Long) {
        transferDao.deleteById(id)
    }

    private fun Transfer.toEntity(): TransferEntity = TransferEntity(
        id = id,
        fromAccountId = fromAccountId,
        toAccountId = toAccountId,
        amountCents = amountCents,
        note = note,
        occurredAt = occurredAt,
        createdAt = System.currentTimeMillis(),
    )

    private fun TransferEntity.toDomain(): Transfer = Transfer(
        id = id,
        fromAccountId = fromAccountId,
        toAccountId = toAccountId,
        amountCents = amountCents,
        note = note,
        occurredAt = occurredAt,
    )
}
