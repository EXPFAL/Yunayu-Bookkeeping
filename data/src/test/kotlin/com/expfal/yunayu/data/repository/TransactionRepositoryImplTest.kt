package com.expfal.yunayu.data.repository

import com.expfal.yunayu.data.local.dao.TransactionDao
import com.expfal.yunayu.data.local.entity.TransactionEntity
import com.expfal.yunayu.domain.model.AccountFilter
import com.expfal.yunayu.domain.model.Transaction
import com.expfal.yunayu.domain.model.TransactionType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** [TransactionRepositoryImpl] 的 JVM 单元测试（手写 fake DAO + coroutines-test）。 */
class TransactionRepositoryImplTest {

    @Test
    fun `add maps expense transaction fields to entity`() = runTest {
        val dao = FakeTransactionDao().apply { nextInsertId = 42L }
        val repository = TransactionRepositoryImpl(dao)
        val now = System.currentTimeMillis()

        val id = repository.add(
            Transaction(
                amountCents = 2500L,
                type = TransactionType.EXPENSE,
                note = null,
                tagId = 3L,
                accountId = 9L,
                occurredAt = 900L,
            ),
        )

        assertEquals(42L, id)
        val entity = dao.inserted.single()
        assertEquals("EXPENSE", entity.type)
        assertTrue(entity.createdAt in (now - 5_000L)..(now + 5_000L))
        assertEquals(2500L, entity.amountCents)
        assertEquals(null, entity.note)
        assertEquals(3L, entity.tagId)
        assertEquals(9L, entity.accountId)
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
        assertEquals(null, entity.accountId)
    }

    @Test
    fun `observeExpenseSumBetween delegates to dao with window args`() = runTest {
        val dao = FakeTransactionDao().apply { expenseSumFlow = flowOf(12_345L) }
        val repository = TransactionRepositoryImpl(dao)

        val sum = repository.observeExpenseSumBetween(100L, 200L).first()

        assertEquals(12_345L, sum)
        assertEquals(listOf(100L to 200L), dao.expenseSumCalls)
    }

    @Test
    fun `observeHeldCents delegates to dao`() = runTest {
        val dao = FakeTransactionDao().apply { heldCentsFlow = flowOf(-5_000L) }
        val repository = TransactionRepositoryImpl(dao)

        val held = repository.observeHeldCents().first()

        assertEquals(-5_000L, held)
    }

    @Test
    fun `observeRecent maps rows including null tagName`() = runTest {
        val dao = FakeTransactionDao().apply {
            recentRowsFlow = flowOf(
                listOf(
                    TransactionDao.RecentTransactionRow(
                        transaction = TransactionEntity(
                            id = 7L,
                            amountCents = 2500L,
                            type = "EXPENSE",
                            note = "买书",
                            tagId = null,
                            occurredAt = 900L,
                            createdAt = 1L,
                        ),
                        tagName = null,
                        tagIcon = null,
                    ),
                ),
            )
        }
        val repository = TransactionRepositoryImpl(dao)

        val recent = repository.observeRecent(5).first()

        assertEquals(1, recent.size)
        val row = recent.single()
        assertEquals(7L, row.id)
        assertEquals(2500L, row.amountCents)
        assertEquals(TransactionType.EXPENSE, row.type)
        assertNull(row.tagName)
        assertEquals(900L, row.occurredAt)
        assertEquals("买书", row.note)
        assertEquals(listOf(5), dao.recentCalls)
    }

    @Test
    fun `delete delegates transaction id to dao`() = runTest {
        val dao = FakeTransactionDao()
        val repository = TransactionRepositoryImpl(dao)

        repository.delete(42L)

        assertEquals(listOf(42L), dao.deletedByIdCalls)
    }

    @Test
    fun `observeFiltered with empty tagIds delegates to non-tag query`() = runTest {
        val dao = FakeTransactionDao()
        val repository = TransactionRepositoryImpl(dao)

        repository.observeFiltered(100L, 200L, emptyList(), "关键词", AccountFilter.All).first()

        assertEquals(1, dao.filteredCalls.size)
        assertEquals(
            FilterCall(100L, 200L, "关键词", TransactionDao.ACCOUNT_MODE_ALL, null),
            dao.filteredCalls.single(),
        )
        assertTrue(dao.filteredByTagsCalls.isEmpty())
    }

    @Test
    fun `observeFiltered with tagIds delegates to by-tags query`() = runTest {
        val dao = FakeTransactionDao()
        val repository = TransactionRepositoryImpl(dao)

        repository.observeFiltered(null, null, listOf(1L, 2L), null, AccountFilter.All).first()

        assertEquals(1, dao.filteredByTagsCalls.size)
        assertEquals(
            FilterByTagsCall(null, null, null, listOf(1L, 2L), TransactionDao.ACCOUNT_MODE_ALL, null),
            dao.filteredByTagsCalls.single(),
        )
        assertTrue(dao.filteredCalls.isEmpty())
    }

    @Test
    fun `observeFiltered maps account filter to dao mode and id`() = runTest {
        val dao = FakeTransactionDao()
        val repository = TransactionRepositoryImpl(dao)

        repository.observeFiltered(null, null, emptyList(), null, AccountFilter.All).first()
        repository.observeFiltered(null, null, emptyList(), null, AccountFilter.Unspecified).first()
        repository.observeFiltered(null, null, emptyList(), null, AccountFilter.Specific(7L)).first()

        assertEquals(
            listOf(
                FilterCall(null, null, null, TransactionDao.ACCOUNT_MODE_ALL, null),
                FilterCall(null, null, null, TransactionDao.ACCOUNT_MODE_UNSPECIFIED, null),
                FilterCall(null, null, null, TransactionDao.ACCOUNT_MODE_SPECIFIC, 7L),
            ),
            dao.filteredCalls,
        )
    }

    @Test
    fun `observeFiltered escapes like wildcards in keyword`() = runTest {
        val dao = FakeTransactionDao()
        val repository = TransactionRepositoryImpl(dao)

        repository.observeFiltered(null, null, emptyList(), "100%_a\\b", AccountFilter.All).first()

        assertEquals("""100\%\_a\\b""", dao.filteredCalls.single().noteKeyword)
    }

    @Test
    fun `observeFiltered treats blank keyword as null`() = runTest {
        val dao = FakeTransactionDao()
        val repository = TransactionRepositoryImpl(dao)

        repository.observeFiltered(null, null, emptyList(), "   ", AccountFilter.All).first()

        assertEquals(null, dao.filteredCalls.single().noteKeyword)
    }

    @Test
    fun `observeUncategorizedCount delegates to dao`() = runTest {
        val dao = FakeTransactionDao().apply { uncategorizedCountFlow = flowOf(7) }
        val repository = TransactionRepositoryImpl(dao)

        val count = repository.observeUncategorizedCount().first()

        assertEquals(7, count)
    }

    @Test
    fun `getUncategorized maps rows including note`() = runTest {
        val dao = FakeTransactionDao().apply {
            uncategorizedRows = listOf(
                TransactionDao.RecentTransactionRow(
                    transaction = TransactionEntity(
                        id = 9L,
                        amountCents = 1_200L,
                        type = "EXPENSE",
                        note = "未分类买书",
                        tagId = null,
                        occurredAt = 800L,
                        createdAt = 1L,
                    ),
                    tagName = null,
                    tagIcon = null,
                ),
            )
        }
        val repository = TransactionRepositoryImpl(dao)

        val rows = repository.getUncategorized()

        assertEquals(1, rows.size)
        assertEquals(9L, rows.single().id)
        assertEquals("未分类买书", rows.single().note)
        assertNull(rows.single().tagName)
    }

    @Test
    fun `assignTags delegates to dao applyTagAssignments`() = runTest {
        val dao = FakeTransactionDao()
        val repository = TransactionRepositoryImpl(dao)
        val assignments = mapOf(1L to listOf(10L, 11L), 2L to listOf(20L))

        repository.assignTags(assignments)

        assertEquals(listOf(assignments), dao.applyTagAssignmentsCalls)
    }

    @Test
    fun `assignTags with empty map skips dao`() = runTest {
        val dao = FakeTransactionDao()
        val repository = TransactionRepositoryImpl(dao)

        repository.assignTags(emptyMap())

        assertTrue(dao.applyTagAssignmentsCalls.isEmpty())
    }

    @Test
    fun `getOccurredAtsByTagIds delegates to dao`() = runTest {
        val dao = FakeTransactionDao().apply { occurredAtsByTagIdsResult = listOf(100L, 200L) }
        val repository = TransactionRepositoryImpl(dao)

        val occurred = repository.getOccurredAtsByTagIds(listOf(1L, 2L))

        assertEquals(listOf(100L, 200L), occurred)
        assertEquals(listOf(listOf(1L, 2L)), dao.occurredAtsByTagIdsCalls)
    }

    @Test
    fun `getOccurredAtsByTagIds with empty ids skips dao`() = runTest {
        val dao = FakeTransactionDao()
        val repository = TransactionRepositoryImpl(dao)

        val occurred = repository.getOccurredAtsByTagIds(emptyList())

        assertEquals(emptyList<Long>(), occurred)
        assertTrue(dao.occurredAtsByTagIdsCalls.isEmpty())
    }
}
