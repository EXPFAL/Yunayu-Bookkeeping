package com.expfal.yunayu.domain.usecase

import com.expfal.yunayu.domain.model.Tag
import com.expfal.yunayu.domain.model.TagDeleteImpact
import com.expfal.yunayu.domain.repository.TagRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** [GetRecentCategoriesUseCase] 的 JVM 单元测试（手写 fake 仓储 + coroutines-test）。 */
class GetRecentCategoriesUseCaseTest {

    private val now = 1_700_000_000_000L
    private val sevenDaysMillis = 7L * 24 * 60 * 60 * 1000

    @Test
    fun `tops up recent tags with root tags up to limit`() = runTest {
        val recent = listOf(tag(11L, "教材"), tag(12L, "考证"))
        val repository = FakeTagRepository().apply {
            this.recentTags = recent
            this.rootTags = listOf(tag(1L, "学习"), tag(2L, "社交"), tag(3L, "生活"))
        }
        val useCase = GetRecentCategoriesUseCase(repository)

        val result = useCase(now)

        assertEquals(
            listOf(tag(11L, "教材"), tag(12L, "考证"), tag(1L, "学习"), tag(2L, "社交")),
            result,
        )
    }

    @Test
    fun `queries repository with since equal to now minus seven days`() = runTest {
        val repository = FakeTagRepository().apply { recentTags = listOf(tag(11L, "教材")) }
        val useCase = GetRecentCategoriesUseCase(repository)

        useCase(now)

        assertEquals(now - sevenDaysMillis, repository.lastSince)
        assertEquals(4, repository.lastLimit)
    }

    @Test
    fun `falls back to root tags when recent result empty`() = runTest {
        val root = listOf(tag(1L, "学习"), tag(2L, "社交"))
        val repository = FakeTagRepository().apply {
            this.recentTags = emptyList()
            this.rootTags = root
        }
        val useCase = GetRecentCategoriesUseCase(repository)

        val result = useCase(now)

        assertEquals(root, result)
    }

    @Test
    fun `fills up to four when only one recent tag`() = runTest {
        val repository = FakeTagRepository().apply {
            this.recentTags = listOf(tag(11L, "教材"))
            this.rootTags = listOf(tag(1L, "学习"), tag(2L, "社交"), tag(3L, "生活"), tag(4L, "娱乐"))
        }
        val useCase = GetRecentCategoriesUseCase(repository)

        val result = useCase(now)

        assertEquals(4, result.size)
        assertEquals(
            listOf(tag(11L, "教材"), tag(1L, "学习"), tag(2L, "社交"), tag(3L, "生活")),
            result,
        )
    }

    @Test
    fun `deduplicates root tags already present in recent`() = runTest {
        val repository = FakeTagRepository().apply {
            this.recentTags = listOf(tag(1L, "学习"), tag(11L, "教材"))
            this.rootTags = listOf(tag(1L, "学习"), tag(2L, "社交"), tag(3L, "生活"))
        }
        val useCase = GetRecentCategoriesUseCase(repository)

        val result = useCase(now)

        assertEquals(
            listOf(tag(1L, "学习"), tag(11L, "教材"), tag(2L, "社交"), tag(3L, "生活")),
            result,
        )
    }

    @Test
    fun `does not append roots when recent already has four`() = runTest {
        val recent = listOf(tag(11L, "教材"), tag(12L, "考证"), tag(13L, "实习"), tag(14L, "聚餐"))
        val repository = FakeTagRepository().apply {
            this.recentTags = recent
            this.rootTags = listOf(tag(1L, "学习"), tag(2L, "社交"))
        }
        val useCase = GetRecentCategoriesUseCase(repository)

        val result = useCase(now)

        assertEquals(recent, result)
    }

    @Test
    fun `keeps sub-tags in recent and fills up with roots`() = runTest {
        val recent = listOf(tag(11L, "教材", 1L), tag(12L, "考证", 1L))
        val repository = FakeTagRepository().apply {
            this.recentTags = recent
            this.rootTags = listOf(tag(1L, "学习"), tag(2L, "社交"), tag(3L, "生活"))
        }
        val useCase = GetRecentCategoriesUseCase(repository)

        val result = useCase(now)

        assertEquals(
            listOf(tag(11L, "教材", 1L), tag(12L, "考证", 1L), tag(1L, "学习"), tag(2L, "社交")),
            result,
        )
    }

    @Test
    fun `deduplicates sub-tag against root by id`() = runTest {
        val repository = FakeTagRepository().apply {
            this.recentTags = listOf(tag(1L, "高数", 1L))
            this.rootTags = listOf(tag(1L, "学习"), tag(2L, "社交"), tag(3L, "生活"), tag(4L, "娱乐"))
        }
        val useCase = GetRecentCategoriesUseCase(repository)

        val result = useCase(now)

        assertEquals(
            listOf(tag(1L, "高数", 1L), tag(2L, "社交"), tag(3L, "生活"), tag(4L, "娱乐")),
            result,
        )
    }

    private fun tag(id: Long, name: String, parentId: Long? = null) = Tag(
        id = id,
        name = name,
        parentId = parentId,
        sortOrder = 0,
        icon = null,
        createdAt = 100L,
        updatedAt = 200L,
    )

    /** [TagRepository] 手写 fake：记录聚合查询入参，返回预置结果。 */
    private class FakeTagRepository : TagRepository {

        var recentTags: List<Tag> = emptyList()
        var rootTags: List<Tag> = emptyList()
        var lastSince: Long? = null
        var lastLimit: Int? = null

        override fun observeChildren(parentId: Long?): Flow<List<Tag>> = flowOf(emptyList())

        override suspend fun getChildren(parentId: Long?): List<Tag> = rootTags

        override suspend fun getRecentUsedTags(sinceEpochMillis: Long, limit: Int): List<Tag> {
            lastSince = sinceEpochMillis
            lastLimit = limit
            return recentTags
        }

        override suspend fun updateSortOrder(tags: List<Tag>) = Unit

        override suspend fun addSubTag(parentId: Long, name: String, icon: String?): Long = 0L

        override suspend fun renameTag(tagId: Long, newName: String) = Unit

        override suspend fun getDeleteImpact(tagId: Long): TagDeleteImpact = TagDeleteImpact(0, 0, emptyList())

        override suspend fun deleteTag(tagId: Long) = Unit
    }
}
