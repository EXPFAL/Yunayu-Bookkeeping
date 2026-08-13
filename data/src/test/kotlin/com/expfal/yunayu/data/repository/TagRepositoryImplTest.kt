package com.expfal.yunayu.data.repository

import com.expfal.yunayu.data.local.entity.TagEntity
import com.expfal.yunayu.domain.model.Tag
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
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
        val repository = TagRepositoryImpl(dao)

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
        val repository = TagRepositoryImpl(FakeTagDao())

        val children = repository.getChildren(999L)

        assertEquals(emptyList<Tag>(), children)
    }
}
