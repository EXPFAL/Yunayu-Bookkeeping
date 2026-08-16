package com.expfal.yunayu.domain.usecase

import com.expfal.yunayu.domain.model.IncomeTags
import com.expfal.yunayu.domain.model.Tag
import com.expfal.yunayu.domain.model.TagDeleteImpact
import com.expfal.yunayu.domain.model.TransactionType
import com.expfal.yunayu.domain.repository.TagRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** [GetRecentCategoriesUseCase] 的 JVM 单元测试（手写 fake 仓储 + coroutines-test，叶子语义）。 */
class GetRecentCategoriesUseCaseTest {

    private val now = 1_700_000_000_000L
    private val sevenDaysMillis = 7L * 24 * 60 * 60 * 1000

    @Test
    fun `filters non-leaf root from recent keeping only leaf`() = runTest {
        val repository = FakeTagRepository().apply {
            rootTags = listOf(tag(1L, "学习"))
            childrenByParent = mapOf(1L to listOf(tag(11L, "教材", 1L)))
            recentTags = listOf(tag(1L, "学习"), tag(11L, "教材", 1L))
        }
        val useCase = GetRecentCategoriesUseCase(repository)

        val result = useCase(TransactionType.EXPENSE, now)

        assertEquals(listOf(tag(11L, "教材", 1L)), result)
    }

    @Test
    fun `flattens expense root children when recent empty`() = runTest {
        val repository = FakeTagRepository().apply {
            rootTags = listOf(tag(1L, "学习"), tag(2L, "社交"))
            childrenByParent = mapOf(
                1L to listOf(tag(11L, "教材", 1L), tag(12L, "考证", 1L)),
                2L to listOf(tag(21L, "聚餐", 2L)),
            )
        }
        val useCase = GetRecentCategoriesUseCase(repository)

        val result = useCase(TransactionType.EXPENSE, now)

        assertEquals(
            listOf(tag(11L, "教材", 1L), tag(12L, "考证", 1L), tag(21L, "聚餐", 2L)),
            result,
        )
    }

    @Test
    fun `deduplicates fallback against recent leaves`() = runTest {
        val repository = FakeTagRepository().apply {
            rootTags = listOf(tag(1L, "学习"))
            childrenByParent = mapOf(1L to listOf(tag(11L, "教材", 1L), tag(12L, "考证", 1L)))
            recentTags = listOf(tag(11L, "教材", 1L))
        }
        val useCase = GetRecentCategoriesUseCase(repository)

        val result = useCase(TransactionType.EXPENSE, now)

        assertEquals(listOf(tag(11L, "教材", 1L), tag(12L, "考证", 1L)), result)
    }

    @Test
    fun `does not top up when recent already has four`() = runTest {
        val recent = listOf(
            tag(11L, "教材", 1L),
            tag(12L, "考证", 1L),
            tag(13L, "实习", 1L),
            tag(14L, "聚餐", 2L),
        )
        val repository = FakeTagRepository().apply {
            rootTags = listOf(tag(1L, "学习"), tag(2L, "社交"))
            childrenByParent = mapOf(
                1L to listOf(tag(11L, "教材", 1L), tag(12L, "考证", 1L), tag(13L, "实习", 1L)),
                2L to listOf(tag(14L, "聚餐", 2L)),
            )
            this.recentTags = recent
        }
        val useCase = GetRecentCategoriesUseCase(repository)

        val result = useCase(TransactionType.EXPENSE, now)

        assertEquals(recent, result)
    }

    @Test
    fun `income fallback returns only income root children not root itself`() = runTest {
        val repository = FakeTagRepository().apply {
            rootTags = listOf(tag(1L, "学习"), tag(2L, IncomeTags.INCOME_ROOT_NAME), tag(3L, "社交"))
            childrenByParent = mapOf(
                2L to listOf(tag(21L, "生活费", 2L), tag(22L, "兼职经营", 2L)),
                1L to listOf(tag(11L, "教材", 1L)),
            )
        }
        val useCase = GetRecentCategoriesUseCase(repository)

        val result = useCase(TransactionType.INCOME, now)

        assertEquals(
            listOf(tag(21L, "生活费", 2L), tag(22L, "兼职经营", 2L)),
            result,
        )
    }

    @Test
    fun `expense fallback excludes income root subtree`() = runTest {
        val repository = FakeTagRepository().apply {
            rootTags = listOf(tag(1L, "学习"), tag(2L, IncomeTags.INCOME_ROOT_NAME), tag(3L, "社交"))
            childrenByParent = mapOf(
                1L to listOf(tag(11L, "教材", 1L)),
                2L to listOf(tag(21L, "生活费", 2L)),
                3L to listOf(tag(31L, "聚餐", 3L)),
            )
        }
        val useCase = GetRecentCategoriesUseCase(repository)

        val result = useCase(TransactionType.EXPENSE, now)

        assertEquals(listOf(tag(11L, "教材", 1L), tag(31L, "聚餐", 3L)), result)
    }

    @Test
    fun `treats root without children as leaf`() = runTest {
        val repository = FakeTagRepository().apply {
            rootTags = listOf(tag(1L, "学习"), tag(2L, "社交"))
            childrenByParent = mapOf(1L to listOf(tag(11L, "教材", 1L)))
        }
        val useCase = GetRecentCategoriesUseCase(repository)

        val result = useCase(TransactionType.EXPENSE, now)

        assertEquals(listOf(tag(11L, "教材", 1L), tag(2L, "社交")), result)
    }

    @Test
    fun `degrades to recent as-is when root query fails`() = runTest {
        val recent = listOf(tag(1L, "学习"), tag(11L, "教材", 1L))
        val repository = FakeTagRepository().apply {
            rootError = RuntimeException("db down")
            recentTags = recent
        }
        val useCase = GetRecentCategoriesUseCase(repository)

        val result = useCase(TransactionType.EXPENSE, now)

        assertEquals(recent, result)
    }

    @Test
    fun `degrades to recent as-is when child query fails`() = runTest {
        val recent = listOf(tag(1L, "学习"), tag(11L, "教材", 1L))
        val repository = FakeTagRepository().apply {
            rootTags = listOf(tag(1L, "学习"))
            childError = RuntimeException("db down")
            recentTags = recent
        }
        val useCase = GetRecentCategoriesUseCase(repository)

        val result = useCase(TransactionType.EXPENSE, now)

        assertEquals(recent, result)
    }

    @Test
    fun `queries repository with since equal to now minus seven days`() = runTest {
        val repository = FakeTagRepository().apply { recentTags = listOf(tag(11L, "教材", 1L)) }
        val useCase = GetRecentCategoriesUseCase(repository)

        useCase(TransactionType.EXPENSE, now)

        assertEquals(now - sevenDaysMillis, repository.lastSince)
        assertEquals(4, repository.lastLimit)
    }

    @Test
    fun `passes type through to repository`() = runTest {
        val repository = FakeTagRepository().apply { recentTags = listOf(tag(11L, "教材", 1L)) }
        val useCase = GetRecentCategoriesUseCase(repository)

        useCase(TransactionType.INCOME, now)

        assertEquals(TransactionType.INCOME, repository.lastType)
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

    /** [TagRepository] 手写 fake：记录聚合查询入参，返回预置结果，可注入查询异常模拟降级。 */
    private class FakeTagRepository : TagRepository {

        var recentTags: List<Tag> = emptyList()
        var rootTags: List<Tag> = emptyList()
        var childrenByParent: Map<Long, List<Tag>> = emptyMap()
        var rootError: Throwable? = null
        var childError: Throwable? = null
        var lastSince: Long? = null
        var lastType: TransactionType? = null
        var lastLimit: Int? = null

        override fun observeChildren(parentId: Long?): Flow<List<Tag>> = flowOf(emptyList())

        override suspend fun getChildren(parentId: Long?): List<Tag> {
            if (parentId == null) {
                rootError?.let { throw it }
                return rootTags
            }
            childError?.let { throw it }
            return childrenByParent[parentId] ?: emptyList()
        }

        override suspend fun getRecentUsedTags(sinceEpochMillis: Long, type: TransactionType, limit: Int): List<Tag> {
            lastSince = sinceEpochMillis
            lastType = type
            lastLimit = limit
            return recentTags
        }

        override suspend fun updateSortOrder(tags: List<Tag>) = Unit

        override suspend fun addSubTag(parentId: Long, name: String, icon: String?): Long = 0L

        override suspend fun addRootTag(name: String, icon: String?): Long = 0L

        override suspend fun renameTag(tagId: Long, newName: String) = Unit

        override suspend fun getDeleteImpact(tagId: Long): TagDeleteImpact = TagDeleteImpact(0, 0, emptyList())

        override suspend fun deleteTag(tagId: Long) = Unit

        override suspend fun mergeTags(keepTagId: Long, dropTagId: Long) = Unit
    }
}
