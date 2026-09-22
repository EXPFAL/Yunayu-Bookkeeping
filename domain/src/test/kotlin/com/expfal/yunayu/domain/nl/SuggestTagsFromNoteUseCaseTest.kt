package com.expfal.yunayu.domain.nl

import com.expfal.yunayu.domain.model.Tag
import com.expfal.yunayu.domain.model.TagDeleteImpact
import com.expfal.yunayu.domain.model.TransactionType
import com.expfal.yunayu.domain.repository.TagRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** [SuggestTagsFromNoteUseCase] 的 JVM 单元测试。 */
class SuggestTagsFromNoteUseCaseTest {

    @Test
    fun `returns empty for blank note without calling engine`() = runTest {
        val parser = FakeParser(available = true)
        val useCase = SuggestTagsFromNoteUseCase(parser, FakeTagRepository())

        assertTrue(useCase("  ", TransactionType.EXPENSE).isEmpty())
        assertTrue(parser.availableCalls.isEmpty())
        assertTrue(parser.generateCalls.isEmpty())
    }

    @Test
    fun `returns empty when engine unavailable`() = runTest {
        val parser = FakeParser(available = false)
        val tags = FakeTagRepository().apply {
            rootTags = listOf(Tag(1L, "学习", null))
        }
        val useCase = SuggestTagsFromNoteUseCase(parser, tags)

        assertTrue(useCase("买教材", TransactionType.EXPENSE).isEmpty())
        assertTrue(parser.generateCalls.isEmpty())
    }

    @Test
    fun `attaches existing tags from generate output`() = runTest {
        val book = Tag(2L, "课本教辅", 1L)
        val learning = Tag(1L, "学习", null)
        val parser = FakeParser(available = true).apply {
            generateResult = """[{"tag_name":"学习·课本教辅"}]"""
        }
        val tags = FakeTagRepository().apply {
            rootTags = listOf(learning)
            childrenByParent = mapOf(1L to listOf(book))
        }
        val useCase = SuggestTagsFromNoteUseCase(parser, tags)

        val result = useCase("买教材", TransactionType.EXPENSE)

        assertEquals(listOf(book), result)
        assertEquals(1, parser.generateCalls.size)
    }

    @Test
    fun `returns empty when generate returns null`() = runTest {
        val parser = FakeParser(available = true).apply { generateResult = null }
        val tags = FakeTagRepository().apply {
            rootTags = listOf(Tag(1L, "学习", null))
        }
        val useCase = SuggestTagsFromNoteUseCase(parser, tags)

        assertTrue(useCase("买教材", TransactionType.EXPENSE).isEmpty())
    }

    @Test
    fun `swallows non cancellation failures`() = runTest {
        val parser = FakeParser(available = true).apply {
            generateThrows = RuntimeException("boom")
        }
        val tags = FakeTagRepository().apply {
            rootTags = listOf(Tag(1L, "学习", null))
        }
        val useCase = SuggestTagsFromNoteUseCase(parser, tags)

        assertTrue(useCase("买教材", TransactionType.EXPENSE).isEmpty())
    }

    @Test
    fun `rethrows cancellation exception`() = runTest {
        val parser = FakeParser(available = true).apply {
            generateThrows = CancellationException("cancelled")
        }
        val tags = FakeTagRepository().apply {
            rootTags = listOf(Tag(1L, "学习", null))
        }
        val useCase = SuggestTagsFromNoteUseCase(parser, tags)

        var caught: Throwable? = null
        try {
            useCase("买教材", TransactionType.EXPENSE)
        } catch (t: Throwable) {
            caught = t
        }
        assertTrue(caught is CancellationException)
    }

    private class FakeParser(var available: Boolean = true) : NLTransactionParser {
        var generateResult: String? = "[]"
        var generateThrows: Throwable? = null
        val availableCalls = mutableListOf<Boolean>()
        val generateCalls = mutableListOf<Pair<String, String>>()

        override suspend fun isAvailable(): Boolean {
            availableCalls += available
            return available
        }

        override suspend fun generate(systemInstruction: String, userText: String): String? {
            generateThrows?.let { throw it }
            generateCalls += systemInstruction to userText
            return generateResult
        }
    }

    private class FakeTagRepository : TagRepository {
        var rootTags: List<Tag> = emptyList()
        var childrenByParent: Map<Long, List<Tag>> = emptyMap()

        override fun observeChildren(parentId: Long?): Flow<List<Tag>> = flowOf(emptyList())

        override suspend fun getChildren(parentId: Long?): List<Tag> =
            if (parentId == null) rootTags else childrenByParent[parentId] ?: emptyList()

        override suspend fun getRecentUsedTags(sinceEpochMillis: Long, type: TransactionType, limit: Int): List<Tag> =
            emptyList()

        override suspend fun updateSortOrder(tags: List<Tag>) = Unit

        override suspend fun addSubTag(parentId: Long, name: String, icon: String?): Long = 0L

        override suspend fun addRootTag(name: String, icon: String?): Long = 0L

        override suspend fun renameTag(tagId: Long, newName: String) = Unit

        override suspend fun getDeleteImpact(tagId: Long): TagDeleteImpact =
            TagDeleteImpact(0, 0, emptyList())

        override suspend fun deleteTag(tagId: Long) = Unit

        override suspend fun mergeTags(keepTagId: Long, dropTagId: Long) = Unit
    }
}
