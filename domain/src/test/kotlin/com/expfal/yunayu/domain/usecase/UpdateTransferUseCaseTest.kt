package com.expfal.yunayu.domain.usecase

import com.expfal.yunayu.domain.model.Transfer
import com.expfal.yunayu.domain.repository.TransferRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class UpdateTransferUseCaseTest {

    @Test
    fun `updates transfer when valid`() = runTest {
        val repo = FakeTransferRepository()
        val useCase = UpdateTransferUseCase(repo)
        val transfer = Transfer(
            id = 9L,
            fromAccountId = 1L,
            toAccountId = 2L,
            amountCents = 500L,
            note = "调账",
            occurredAt = 100L,
        )

        useCase(transfer)

        assertEquals(listOf(transfer), repo.updated)
    }

    @Test
    fun `rejects same accounts`() = runTest {
        val repo = FakeTransferRepository()
        val useCase = UpdateTransferUseCase(repo)
        val error = runCatching {
            useCase(
                Transfer(
                    id = 9L,
                    fromAccountId = 1L,
                    toAccountId = 1L,
                    amountCents = 500L,
                    occurredAt = 100L,
                ),
            )
        }.exceptionOrNull()

        assertTrue(error is IllegalArgumentException)
        assertTrue(repo.updated.isEmpty())
    }

    @Test
    fun `rejects non-positive amount`() = runTest {
        val repo = FakeTransferRepository()
        val useCase = UpdateTransferUseCase(repo)
        val error = runCatching {
            useCase(
                Transfer(
                    id = 9L,
                    fromAccountId = 1L,
                    toAccountId = 2L,
                    amountCents = 0L,
                    occurredAt = 100L,
                ),
            )
        }.exceptionOrNull()

        assertTrue(error is IllegalArgumentException)
        assertTrue(repo.updated.isEmpty())
    }

    private class FakeTransferRepository : TransferRepository {
        val updated = mutableListOf<Transfer>()

        override fun observeTransfers(): Flow<List<Transfer>> = flowOf(emptyList())

        override suspend fun getById(id: Long): Transfer? = null

        override suspend fun insertTransfer(transfer: Transfer): Long = 0L

        override suspend fun updateTransfer(transfer: Transfer) {
            updated += transfer
        }

        override suspend fun deleteById(id: Long) = Unit
    }
}
