package com.expfal.yunayu.domain.usecase

import com.expfal.yunayu.domain.model.DuplicateTagNameException
import com.expfal.yunayu.domain.model.ExpenseSeedTags
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

/** [EnsureExpenseTagsUseCase] 的 JVM 单元测试。 */
class EnsureExpenseTagsUseCaseTest {

    @Test
    fun `skips when life root is missing`() = runTest {
        val result = EnsureExpenseTagsUseCase(FakeTagRepository()).invoke()

        assertFalse(result.rootFound)
        assertTrue(result.createdChildren.isEmpty())
        assertTrue(result.skippedChildren.isEmpty())
    }

    @Test
    fun `creates missing incremental children`() = runTest {
        val repository = FakeTagRepository().apply {
            addRoot(lifeRoot)
            addChild(lifeRoot.id, tag(11L, "餐饮", lifeRoot.id))
        }
        val result = EnsureExpenseTagsUseCase(repository).invoke()

        assertTrue(result.rootFound)
        assertEquals(ExpenseSeedTags.ENSURE_LIFE_CHILDREN, result.createdChildren)
        assertEquals(ExpenseSeedTags.ENSURE_LIFE_CHILDREN.size, repository.addedSubTags.size)
        assertFalse(repository.addedSubTags.any { it.second == "洗衣" })
    }

    @Test
    fun `is idempotent when incremental children exist`() = runTest {
        val repository = FakeTagRepository().apply {
            addRoot(lifeRoot)
            ExpenseSeedTags.ENSURE_LIFE_CHILDREN.forEachIndexed { index, name ->
                addChild(lifeRoot.id, tag(20L + index, name, lifeRoot.id))
            }
        }
        val result = EnsureExpenseTagsUseCase(repository).invoke()

        assertTrue(result.rootFound)
        assertTrue(result.createdChildren.isEmpty())
        assertTrue(repository.addedSubTags.isEmpty())
    }

    @Test
    fun `counts duplicate race as skipped`() = runTest {
        val repository = FakeTagRepository().apply {
            addRoot(lifeRoot)
            duplicateOnAdd = true
        }
        val result = EnsureExpenseTagsUseCase(repository).invoke()

        assertTrue(result.rootFound)
        assertTrue(result.createdChildren.isEmpty())
        assertEquals(ExpenseSeedTags.ENSURE_LIFE_CHILDREN, result.skippedChildren)
    }

    private val lifeRoot = tag(1L, ExpenseSeedTags.LIFE_ROOT_NAME)

    private fun tag(id: Long, name: String, parentId: Long? = null) = Tag(
        id = id,
        name = name,
        parentId = parentId,
        sortOrder = 0,
        icon = null,
        createdAt = 100L,
        updatedAt = 200L,
    )

    private class FakeTagRepository : TagRepository {

        private val roots = mutableListOf<Tag>()
        private val childrenByParent = mutableMapOf<Long, MutableList<Tag>>()
        val addedSubTags = mutableListOf<Triple<Long, String, String?>>()
        var duplicateOnAdd = false
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

        override suspend fun addRootTag(name: String, icon: String?): Long = 0L

        override suspend fun renameTag(tagId: Long, newName: String) = Unit

        override suspend fun getDeleteImpact(tagId: Long): TagDeleteImpact = TagDeleteImpact(0, 0, emptyList())

        override suspend fun deleteTag(tagId: Long) = Unit

        override suspend fun mergeTags(keepTagId: Long, dropTagId: Long) = Unit
    }
}
