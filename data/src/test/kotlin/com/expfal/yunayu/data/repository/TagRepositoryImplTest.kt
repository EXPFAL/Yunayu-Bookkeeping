package com.expfal.yunayu.data.repository

import com.expfal.yunayu.data.local.dao.TransactionDao
import com.expfal.yunayu.data.local.entity.TagEntity
import com.expfal.yunayu.domain.model.DuplicateTagNameException
import com.expfal.yunayu.domain.model.Tag
import com.expfal.yunayu.domain.model.TagDeleteImpact
import com.expfal.yunayu.domain.model.TransactionType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** [TagRepositoryImpl] 的 JVM 单元测试（手写 fake DAO + coroutines-test）。 */
class TagRepositoryImplTest {

    @Test
    fun `getChildren maps entities to domain models`() = runTest {
        val dao = FakeTagDao().apply {
            childrenByParent = mapOf(
                1L to listOf(
                    TagEntity(11L, "教材", 1L, 0, "📖", 100L, 200L),
                    TagEntity(12L, "考证", 1L, 1, null, 101L, 201L),
                ),
            )
        }
        val repository = TagRepositoryImpl(dao, FakeTransactionDao())

        val children = repository.getChildren(1L)

        assertEquals(2, children.size)
        assertEquals(
            Tag(id = 11L, name = "教材", parentId = 1L, sortOrder = 0, icon = "📖", createdAt = 100L, updatedAt = 200L),
            children[0],
        )
        assertEquals(
            Tag(id = 12L, name = "考证", parentId = 1L, sortOrder = 1, icon = null, createdAt = 101L, updatedAt = 201L),
            children[1],
        )
    }

    @Test
    fun `getChildren returns empty list for missing parent`() = runTest {
        val repository = TagRepositoryImpl(FakeTagDao(), FakeTransactionDao())

        val children = repository.getChildren(999L)

        assertEquals(emptyList<Tag>(), children)
    }

    @Test
    fun `getRecentUsedTags maps rows to domain tags and passes through args`() = runTest {
        val transactionDao = FakeTransactionDao().apply {
            recentTagRows = listOf(
                TransactionDao.RecentTagRow(
                    tag = TagEntity(11L, "教材", 1L, 0, "📖", 100L, 200L),
                    usageCount = 5L,
                ),
                TransactionDao.RecentTagRow(
                    tag = TagEntity(12L, "考证", 1L, 1, null, 101L, 201L),
                    usageCount = 3L,
                ),
            )
        }
        val repository = TagRepositoryImpl(FakeTagDao(), transactionDao)

        val tags = repository.getRecentUsedTags(sinceEpochMillis = 1000L, type = TransactionType.INCOME, limit = 4)

        assertEquals(
            listOf(
                Tag(id = 11L, name = "教材", parentId = 1L, sortOrder = 0, icon = "📖", createdAt = 100L, updatedAt = 200L),
                Tag(id = 12L, name = "考证", parentId = 1L, sortOrder = 1, icon = null, createdAt = 101L, updatedAt = 201L),
            ),
            tags,
        )
        assertEquals(listOf(Triple(1000L, "INCOME", 4)), transactionDao.recentFrequentTagsCalls)
    }

    @Test
    fun `getRecentUsedTags returns empty list when no rows`() = runTest {
        val repository = TagRepositoryImpl(FakeTagDao(), FakeTransactionDao())

        val tags = repository.getRecentUsedTags(sinceEpochMillis = 0L, type = TransactionType.EXPENSE, limit = 4)

        assertEquals(emptyList<Tag>(), tags)
    }

    @Test
    fun `addSubTag inserts with next sort order and trimmed name`() = runTest {
        val dao = FakeTagDao().apply {
            countByNameResult = 0
            nextSortOrderByParent = mapOf(1L to 5)
            nextInsertId = 42L
        }
        val repository = TagRepositoryImpl(dao, FakeTransactionDao())

        val id = repository.addSubTag(parentId = 1L, name = "  教材  ", icon = "📖")

        assertEquals(42L, id)
        assertEquals(listOf(1L to "教材"), dao.countByNameCalls)
        assertEquals(1, dao.insertedTags.size)
        val inserted = dao.insertedTags.single()
        assertEquals("教材", inserted.name)
        assertEquals(1L, inserted.parentId)
        assertEquals(5, inserted.sortOrder)
        assertEquals("📖", inserted.icon)
        assertEquals(inserted.createdAt, inserted.updatedAt)
        assertTrue(inserted.createdAt > 0L)
    }

    @Test
    fun `addSubTag throws DuplicateTagNameException on duplicate name`() = runTest {
        val dao = FakeTagDao().apply { countByNameResult = 1 }
        val repository = TagRepositoryImpl(dao, FakeTransactionDao())

        val error = captureError { repository.addSubTag(1L, "教材") }

        assertEquals(DuplicateTagNameException::class.java, error?.javaClass)
    }

    @Test
    fun `addSubTag rejects blank name`() = runTest {
        val repository = TagRepositoryImpl(FakeTagDao(), FakeTransactionDao())

        val error = captureError { repository.addSubTag(1L, "   ") }

        assertEquals(IllegalArgumentException::class.java, error?.javaClass)
        assertEquals("标签名不可为空", error?.message)
    }

    @Test
    fun `renameTag rejects root tag`() = runTest {
        val dao = FakeTagDao().apply { allTags = listOf(tagEntity(1L, "学习")) }
        val repository = TagRepositoryImpl(dao, FakeTransactionDao())

        val error = captureError { repository.renameTag(1L, "新名") }

        assertEquals(IllegalArgumentException::class.java, error?.javaClass)
        assertEquals("根标签不可重命名", error?.message)
    }

    @Test
    fun `renameTag throws DuplicateTagNameException on sibling duplicate`() = runTest {
        val dao = FakeTagDao().apply {
            allTags = listOf(
                tagEntity(2L, "教材", 1L),
                tagEntity(3L, "考证", 1L),
            )
        }
        val repository = TagRepositoryImpl(dao, FakeTransactionDao())

        val error = captureError { repository.renameTag(3L, "教材") }

        assertEquals(DuplicateTagNameException::class.java, error?.javaClass)
    }

    @Test
    fun `renameTag renames non-root tag with trimmed name`() = runTest {
        val dao = FakeTagDao().apply { allTags = listOf(tagEntity(2L, "教材", 1L)) }
        val repository = TagRepositoryImpl(dao, FakeTransactionDao())

        repository.renameTag(2L, "  高数  ")

        assertEquals(1, dao.renameCalls.size)
        val (id, name, updatedAt) = dao.renameCalls.single()
        assertEquals(2L, id)
        assertEquals("高数", name)
        assertTrue(updatedAt > 0L)
    }

    @Test
    fun `renameTag throws when rename affects zero rows`() = runTest {
        val dao = FakeTagDao().apply {
            allTags = listOf(tagEntity(2L, "教材", 1L))
            renameResult = 0
        }
        val repository = TagRepositoryImpl(dao, FakeTransactionDao())

        val error = captureError { repository.renameTag(2L, "高数") }

        assertEquals(IllegalArgumentException::class.java, error?.javaClass)
        assertEquals("标签不存在：2", error?.message)
    }

    @Test
    fun `deleteTag rejects root tag`() = runTest {
        val dao = FakeTagDao().apply { allTags = listOf(tagEntity(1L, "学习")) }
        val repository = TagRepositoryImpl(dao, FakeTransactionDao())

        val error = captureError { repository.deleteTag(1L) }

        assertEquals(IllegalArgumentException::class.java, error?.javaClass)
        assertEquals("根标签不可删除", error?.message)
    }

    @Test
    fun `deleteTag deletes non-root tag by id`() = runTest {
        val dao = FakeTagDao().apply { allTags = listOf(tagEntity(2L, "教材", 1L)) }
        val repository = TagRepositoryImpl(dao, FakeTransactionDao())

        repository.deleteTag(2L)

        assertEquals(listOf(2L), dao.deleteCalls)
    }

    @Test
    fun `getDeleteImpact counts deep subtree beyond three levels`() = runTest {
        val dao = FakeTagDao().apply {
            allTags = listOf(
                tagEntity(1L, "学习"),
                tagEntity(2L, "教材", 1L),
                tagEntity(3L, "高数", 2L),
                tagEntity(4L, "习题", 3L),
                tagEntity(5L, "考证", 1L),
            )
        }
        val transactionDao = FakeTransactionDao().apply { countByTagIdsResult = 7 }
        val repository = TagRepositoryImpl(dao, transactionDao)

        val impact = repository.getDeleteImpact(2L)

        assertEquals(TagDeleteImpact(3, 7, listOf("教材", "高数", "习题")), impact)
        assertEquals(listOf(listOf(2L, 3L, 4L)), transactionDao.countByTagIdsCalls)
    }

    @Test
    fun `getDeleteImpact handles cyclic data without infinite loop`() = runTest {
        val dao = FakeTagDao().apply {
            allTags = listOf(
                tagEntity(2L, "A", 3L),
                tagEntity(3L, "B", 2L),
            )
        }
        val repository = TagRepositoryImpl(dao, FakeTransactionDao())

        val impact = repository.getDeleteImpact(2L)

        assertEquals(2, impact.subtreeNodeCount)
        assertEquals(listOf("A", "B"), impact.subtreeNames)
    }

    @Test
    fun `getDeleteImpact throws on missing tag`() = runTest {
        val repository = TagRepositoryImpl(FakeTagDao(), FakeTransactionDao())

        val error = captureError { repository.getDeleteImpact(999L) }

        assertEquals(IllegalArgumentException::class.java, error?.javaClass)
        assertEquals("标签不存在：999", error?.message)
    }

    private fun tagEntity(id: Long, name: String, parentId: Long? = null, sortOrder: Int = 0) =
        TagEntity(id, name, parentId, sortOrder, null, 100L, 200L)

    private suspend fun captureError(block: suspend () -> Unit): Throwable? =
        try {
            block()
            null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            e
        }
}
