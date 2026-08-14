package com.expfal.yunayu.data.repository

import com.expfal.yunayu.domain.model.Transaction
import com.expfal.yunayu.domain.model.TransactionType
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** [TransactionRepositoryImpl] 的 JVM 单元测试（手写 fake DAO + coroutines-test）。 */
class TransactionRepositoryImplTest {

    @Test
    fun `add maps expense transaction fields to entity`() = runTest {
        val dao = FakeTransactionDao().apply { nextInsertId = 42L }
        val repository = TransactionRepositoryImpl(dao)

        val id = repository.add(
            Transaction(
                amountCents = 2500L,
                type = TransactionType.EXPENSE,
                note = null,
                tagId = 3L,
                occurredAt = 900L,
            ),
        )

        assertEquals(42L, id)
        val entity = dao.inserted.single()
        assertEquals("EXPENSE", entity.type)
        assertTrue(entity.createdAt >= 0L)
        assertEquals(2500L, entity.amountCents)
        assertEquals(null, entity.note)
        assertEquals(3L, entity.tagId)
        assertEquals(900L, entity.occurredAt)
    }

    @Test
    fun `add maps income type and delegates insert exactly once`() = runTest {
        val dao = FakeTransactionDao()
        val repository = TransactionRepositoryImpl(dao)

        val id = repository.add(
            Transaction(
                amountCents = 100L,
                type = TransactionType.INCOME,
                note = "奖学金",
                tagId = null,
                occurredAt = 1L,
            ),
        )

        assertEquals(1L, id)
        val entity = dao.inserted.single()
        assertEquals("INCOME", entity.type)
        assertEquals("奖学金", entity.note)
        assertEquals(null, entity.tagId)
    }
}
