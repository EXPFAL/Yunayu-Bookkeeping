package com.expfal.yunayu.domain.usecase

import com.expfal.yunayu.domain.model.Transfer
import com.expfal.yunayu.domain.repository.TransferRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * [RecordTransferUseCase] 的 JVM 单元测试（手写 fake 仓储 + coroutines-test）。
 *
 * 转账与收支统计 / 月度预算 / 报告 / 标签推荐完全隔离：本用例构造函数仅注入
 * [TransferRepository]，报告标脏与预算仓储在结构上不可达——落库仅调用一次
 * `insertTransfer` 即是对隔离边界的有力断言。
 */
class RecordTransferUseCaseTest {

    @Test
    fun `rejects when fromAccountId is zero`() = runTest {
        val useCase = RecordTransferUseCase(FakeTransferRepository())

        val error = captureError { useCase(fromAccountId = 0L, toAccountId = 2L, amountCents = 100L) }

        assertEquals(IllegalArgumentException::class.java, error?.javaClass)
    }

    @Test
    fun `rejects when toAccountId is zero`() = runTest {
        val useCase = RecordTransferUseCase(FakeTransferRepository())

        val error = captureError { useCase(fromAccountId = 1L, toAccountId = 0L, amountCents = 100L) }

        assertEquals(IllegalArgumentException::class.java, error?.javaClass)
    }

    @Test
    fun `rejects when from equals to`() = runTest {
        val useCase = RecordTransferUseCase(FakeTransferRepository())

        val error = captureError { useCase(fromAccountId = 3L, toAccountId = 3L, amountCents = 100L) }

        assertEquals(IllegalArgumentException::class.java, error?.javaClass)
    }

    @Test
    fun `rejects non-positive amount`() = runTest {
        val useCase = RecordTransferUseCase(FakeTransferRepository())

        val zeroError = captureError { useCase(fromAccountId = 1L, toAccountId = 2L, amountCents = 0L) }
        val negativeError = captureError { useCase(fromAccountId = 1L, toAccountId = 2L, amountCents = -5L) }

        assertEquals(IllegalArgumentException::class.java, zeroError?.javaClass)
        assertEquals(IllegalArgumentException::class.java, negativeError?.javaClass)
    }

    @Test
    fun `inserts transfer and returns its id`() = runTest {
        val repository = FakeTransferRepository().apply { nextId = 42L }
        val useCase = RecordTransferUseCase(repository)

        val id = useCase(fromAccountId = 1L, toAccountId = 2L, amountCents = 1_234L, note = "调账")

        assertEquals(42L, id)
        val inserted = repository.inserted.single()
        assertEquals(1L, inserted.fromAccountId)
        assertEquals(2L, inserted.toAccountId)
        assertEquals(1_234L, inserted.amountCents)
        assertEquals("调账", inserted.note)
    }

    @Test
    fun `defaults occurredAt to current time`() = runTest {
        val repository = FakeTransferRepository()
        val useCase = RecordTransferUseCase(repository)

        val before = System.currentTimeMillis()
        useCase(fromAccountId = 1L, toAccountId = 2L, amountCents = 100L)
        val after = System.currentTimeMillis()

        val occurredAt = repository.inserted.single().occurredAt
        assertTrue(occurredAt in before..after)
    }

    @Test
    fun `passes occurredAt through when provided`() = runTest {
        val repository = FakeTransferRepository()
        val useCase = RecordTransferUseCase(repository)

        useCase(fromAccountId = 1L, toAccountId = 2L, amountCents = 100L, occurredAt = 500L)

        assertEquals(500L, repository.inserted.single().occurredAt)
    }

    @Test
    fun `records exactly one insert and never deletes - isolated from report and budget`() = runTest {
        val repository = FakeTransferRepository().apply { nextId = 7L }
        val useCase = RecordTransferUseCase(repository)

        useCase(fromAccountId = 1L, toAccountId = 2L, amountCents = 100L)

        assertEquals(1, repository.inserted.size)
        assertTrue(repository.deletedIds.isEmpty())
    }

    private suspend fun captureError(block: suspend () -> Unit): Throwable? =
        try {
            block()
            null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            e
        }

    /** [TransferRepository] 手写 fake：记录插入与删除调用，返回预置主键。 */
    private class FakeTransferRepository : TransferRepository {

        val inserted = mutableListOf<Transfer>()
        var nextId: Long = 0L
        val deletedIds = mutableListOf<Long>()

        override fun observeTransfers(): Flow<List<Transfer>> = flowOf(emptyList())

        override suspend fun insertTransfer(transfer: Transfer): Long {
            inserted += transfer
            return nextId
        }

        override suspend fun deleteById(id: Long) {
            deletedIds += id
        }
    }
}
