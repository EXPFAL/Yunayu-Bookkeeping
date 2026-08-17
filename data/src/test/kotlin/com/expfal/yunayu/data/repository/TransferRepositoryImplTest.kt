package com.expfal.yunayu.data.repository

import com.expfal.yunayu.data.local.entity.TransferEntity
import com.expfal.yunayu.domain.model.Transfer
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** [TransferRepositoryImpl] 的 JVM 单元测试（手写 fake DAO + coroutines-test）。 */
class TransferRepositoryImplTest {

    @Test
    fun `insertTransfer maps fields to entity and returns id`() = runTest {
        val dao = FakeTransferDao().apply { nextInsertId = 42L }
        val repository = TransferRepositoryImpl(dao)
        val now = System.currentTimeMillis()

        val id = repository.insertTransfer(
            Transfer(
                fromAccountId = 1L,
                toAccountId = 2L,
                amountCents = 2_500L,
                note = "转账",
                occurredAt = 900L,
            ),
        )

        assertEquals(42L, id)
        val entity = dao.inserted.single()
        assertEquals(1L, entity.fromAccountId)
        assertEquals(2L, entity.toAccountId)
        assertEquals(2_500L, entity.amountCents)
        assertEquals("转账", entity.note)
        assertEquals(900L, entity.occurredAt)
        assertTrue(entity.createdAt in (now - 5_000L)..(now + 5_000L))
    }

    @Test
    fun `observeTransfers maps entities to domain models`() = runTest {
        val dao = FakeTransferDao().apply {
            observeAllFlow = flowOf(
                listOf(
                    TransferEntity(
                        id = 1L,
                        fromAccountId = 1L,
                        toAccountId = 2L,
                        amountCents = 100L,
                        note = null,
                        occurredAt = 200L,
                        createdAt = 300L,
                    ),
                ),
            )
        }
        val repository = TransferRepositoryImpl(dao)

        val transfers = repository.observeTransfers().first()

        assertEquals(
            listOf(
                Transfer(
                    id = 1L,
                    fromAccountId = 1L,
                    toAccountId = 2L,
                    amountCents = 100L,
                    note = null,
                    occurredAt = 200L,
                ),
            ),
            transfers,
        )
    }

    @Test
    fun `deleteById delegates to dao`() = runTest {
        val dao = FakeTransferDao()
        val repository = TransferRepositoryImpl(dao)

        repository.deleteById(42L)

        assertEquals(listOf(42L), dao.deletedByIdCalls)
    }
}
