package com.expfal.yunayu.domain.usecase

import com.expfal.yunayu.domain.model.DuplicateTagNameException
import com.expfal.yunayu.domain.model.IncomeTags
import com.expfal.yunayu.domain.model.Tag
import com.expfal.yunayu.domain.model.TagDeleteImpact
import com.expfal.yunayu.domain.model.TransactionType
import com.expfal.yunayu.domain.repository.TagRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** [EnsureIncomeTagsUseCase] 的 JVM 单元测试（手写 fake 仓储 + coroutines-test）。 */
class EnsureIncomeTagsUseCaseTest {

    @Test
    fun `creates income root and all seed children on first run`() = runTest {
        val repository = FakeTagRepository()
        val useCase = EnsureIncomeTagsUseCase(repository)

        val result = useCase()

        assertTrue(result.rootCreated)
        assertEquals(IncomeTags.INCOME_SEED_SUB_TAGS, result.createdChildren)
        assertTrue(result.skippedChildren.isEmpty())
        assertEquals(listOf(IncomeTags.INCOME_ROOT_NAME to IncomeTags.INCOME_ROOT_ICON), repository.addedRoots)
        assertEquals(IncomeTags.INCOME_SEED_SUB_TAGS.size, repository.addedSubTags.size)
    }

    @Test
    fun `is idempotent when root and all children exist`() = runTest {
        val repository = FakeTagRepository().apply {
            addRoot(incomeRoot)
            IncomeTags.INCOME_SEED_SUB_TAGS.forEachIndexed { index, name ->
                addChild(incomeRoot.id, tag(100L + index, name, incomeRoot.id))
            }
        }
        val useCase = EnsureIncomeTagsUseCase(repository)

        val result = useCase()

        assertFalse(result.rootCreated)
        assertTrue(result.createdChildren.isEmpty())
        assertTrue(result.skippedChildren.isEmpty())
        assertTrue(repository.addedRoots.isEmpty())
        assertTrue(repository.addedSubTags.isEmpty())
    }

    @Test
    fun `creates only missing children on partial seed`() = runTest {
        val repository = FakeTagRepository().apply {
            addRoot(incomeRoot)
            addChild(incomeRoot.id, tag(101L, "生活费", incomeRoot.id))
        }
        val useCase = EnsureIncomeTagsUseCase(repository)

        val result = useCase()

        assertFalse(result.rootCreated)
        assertEquals(IncomeTags.INCOME_SEED_SUB_TAGS - "生活费", result.createdChildren)
        assertTrue(result.skippedChildren.isEmpty())
        assertEquals(IncomeTags.INCOME_SEED_SUB_TAGS.size - 1, repository.addedSubTags.size)
    }

    @Test
    fun `counts duplicate race as skipped instead of failing`() = runTest {
        val repository = FakeTagRepository().apply {
            addRoot(incomeRoot)
            duplicateOnAdd = true
        }
        val useCase = EnsureIncomeTagsUseCase(repository)

        val result = useCase()

        assertFalse(result.rootCreated)
        assertTrue(result.createdChildren.isEmpty())
        assertEquals(IncomeTags.INCOME_SEED_SUB_TAGS, result.skippedChildren)
    }

    @Test
    fun `counts root duplicate race as skipped and reads back existing root`() = runTest {
        val repository = FakeTagRepository().apply { duplicateOnAddRoot = true }
        val useCase = EnsureIncomeTagsUseCase(repository)

        val result = useCase()

        assertFalse(result.rootCreated)
        assertEquals(IncomeTags.INCOME_SEED_SUB_TAGS, result.createdChildren)
        assertTrue(result.skippedChildren.isEmpty())
    }

    private val incomeRoot = tag(1L, IncomeTags.INCOME_ROOT_NAME)

    private fun tag(id: Long, name: String, parentId: Long? = null) = Tag(
        id = id,
        name = name,
        parentId = parentId,
        sortOrder = 0,
        icon = null,
        createdAt = 100L,
        updatedAt = 200L,
    )

    /** [TagRepository] 手写 fake：维护根/子标签状态，记录新增入参并可注入竞态重名异常。 */
    private class FakeTagRepository : TagRepository {

        private val roots = mutableListOf<Tag>()
        private val childrenByParent = mutableMapOf<Long, MutableList<Tag>>()
        val addedRoots = mutableListOf<Pair<String, String?>>()
        val addedSubTags = mutableListOf<Triple<Long, String, String?>>()
        var duplicateOnAdd = false
        var duplicateOnAddRoot = false
        private var nextId = 500L

        fun addRoot(root: Tag) {
            roots += root
        }

        fun addChild(parentId: Long, child: Tag) {
            childrenByParent.getOrPut(parentId) { mutableListOf() } += child
        }

        override fun observeChildren(parentId: Long?): Flow<List<Tag>> = flowOf(emptyList())

        override suspend fun getChildren(parentId: Long?): List<Tag> =
            if (parentId == null) roots.toList() else childrenByParent[parentId]?.toList() ?: emptyList()

        override suspend fun getRecentUsedTags(sinceEpochMillis: Long, type: TransactionType, limit: Int): List<Tag> =
            emptyList()

        override suspend fun updateSortOrder(tags: List<Tag>) = Unit

        override suspend fun addSubTag(parentId: Long, name: String, icon: String?): Long {
            if (duplicateOnAdd) throw DuplicateTagNameException("dup")
            val child = Tag(id = nextId++, name = name, parentId = parentId)
            childrenByParent.getOrPut(parentId) { mutableListOf() } += child
            addedSubTags += Triple(parentId, name, icon)
            return child.id
        }

        override suspend fun addRootTag(name: String, icon: String?): Long {
            if (duplicateOnAddRoot) {
                // 模拟并发双插竞态：根已由其它协程插入，本调用抛重名异常。
                val root = Tag(id = nextId++, name = name, parentId = null)
                roots += root
                throw DuplicateTagNameException("dup")
            }
            val root = Tag(id = nextId++, name = name, parentId = null)
            roots += root
            addedRoots += name to icon
            return root.id
        }

        override suspend fun renameTag(tagId: Long, newName: String) = Unit

        override suspend fun getDeleteImpact(tagId: Long): TagDeleteImpact = TagDeleteImpact(0, 0, emptyList())

        override suspend fun deleteTag(tagId: Long) = Unit

        override suspend fun mergeTags(keepTagId: Long, dropTagId: Long) = Unit
    }
}
