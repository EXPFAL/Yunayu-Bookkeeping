package com.expfal.yunayu.domain.nl

import com.expfal.yunayu.domain.model.Tag
import com.expfal.yunayu.domain.model.TagDeleteImpact
import com.expfal.yunayu.domain.model.TransactionType
import com.expfal.yunayu.domain.nl.model.NlParseFailure
import com.expfal.yunayu.domain.nl.model.NlParseResult
import com.expfal.yunayu.domain.nl.model.NlTransactionDraft
import com.expfal.yunayu.domain.repository.TagRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** [ParseNaturalLanguageTransactionUseCase] 的 JVM 单元测试（手写 fake）。 */
class ParseNaturalLanguageTransactionUseCaseTest {

    private val now = 1_700_000_000_000L

    @Test
    fun `parses successfully and backfills tagId`() = runTest {
        val parser = FakeNlParser().apply {
            generateResult = "{\"amount\":\"20\",\"tag\":\"生活·餐饮\"}"
        }
        val repository = FakeTagRepository().apply {
            childrenByParent[null] = listOf(tag(1L, "生活"))
            childrenByParent[1L] = listOf(tag(11L, "餐饮", 1L))
        }
        val useCase = ParseNaturalLanguageTransactionUseCase(parser, repository)

        val draft = assertSuccess(useCase("午饭花了20块", now))

        assertEquals(2000L, draft.amountCents)
        assertEquals(TransactionType.EXPENSE, draft.type)
        assertEquals(11L, draft.tagId)
        assertEquals("生活·餐饮", draft.tagPhrase)
    }

    @Test
    fun `rejects blank input`() = runTest {
        val useCase = ParseNaturalLanguageTransactionUseCase(FakeNlParser(), FakeTagRepository())

        assertEquals(
            NlParseResult.Failure(NlParseFailure.EMPTY_INPUT),
            useCase("   ", now),
        )
    }

    @Test
    fun `fails when engine unavailable`() = runTest {
        val parser = FakeNlParser().apply { available = false }
        val useCase = ParseNaturalLanguageTransactionUseCase(parser, FakeTagRepository())

        assertEquals(
            NlParseResult.Failure(NlParseFailure.ENGINE_UNAVAILABLE),
            useCase("午饭20", now),
        )
    }

    @Test
    fun `fails when generate returns null`() = runTest {
        val parser = FakeNlParser().apply { generateResult = null }
        val useCase = ParseNaturalLanguageTransactionUseCase(parser, FakeTagRepository())

        assertEquals(
            NlParseResult.Failure(NlParseFailure.ENGINE_UNAVAILABLE),
            useCase("午饭20", now),
        )
    }

    @Test
    fun `fails with malformed output when no json emitted`() = runTest {
        val parser = FakeNlParser().apply { generateResult = "没有 JSON 输出" }
        val useCase = ParseNaturalLanguageTransactionUseCase(parser, FakeTagRepository())

        assertEquals(
            NlParseResult.Failure(NlParseFailure.MALFORMED_OUTPUT),
            useCase("午饭20", now),
        )
    }

    @Test
    fun `fails with no amount when json lacks amount`() = runTest {
        val parser = FakeNlParser().apply { generateResult = "{\"note\":\"午饭\"}" }
        val useCase = ParseNaturalLanguageTransactionUseCase(parser, FakeTagRepository())

        assertEquals(
            NlParseResult.Failure(NlParseFailure.NO_AMOUNT),
            useCase("午饭20", now),
        )
    }

    @Test
    fun `leaves tagId null when tag phrase unmatched`() = runTest {
        val parser = FakeNlParser().apply {
            generateResult = "{\"amount\":\"20\",\"tag\":\"不存在·标签\"}"
        }
        val repository = FakeTagRepository().apply {
            childrenByParent[null] = listOf(tag(1L, "学习"))
        }
        val useCase = ParseNaturalLanguageTransactionUseCase(parser, repository)

        val draft = assertSuccess(useCase("午饭20", now))

        assertNull(draft.tagId)
    }

    @Test
    fun `degrades to failure when engine throws`() = runTest {
        val parser = FakeNlParser().apply { generateThrows = RuntimeException("boom") }
        val useCase = ParseNaturalLanguageTransactionUseCase(parser, FakeTagRepository())

        assertEquals(
            NlParseResult.Failure(NlParseFailure.ENGINE_UNAVAILABLE),
            useCase("午饭20", now),
        )
    }

    @Test
    fun `passes user text and built instruction to engine`() = runTest {
        val parser = FakeNlParser()
        val repository = FakeTagRepository().apply {
            childrenByParent[null] = listOf(tag(1L, "生活"))
            childrenByParent[1L] = listOf(tag(11L, "餐饮", 1L))
        }
        val useCase = ParseNaturalLanguageTransactionUseCase(parser, repository)

        useCase("午饭20", now)

        val call = parser.generateCalls.single()
        assertEquals("午饭20", call.second)
        assertTrue(call.first.contains("生活"))
        assertTrue(call.first.contains("生活·餐饮"))
    }

    private fun assertSuccess(result: NlParseResult): NlTransactionDraft =
        (result as? NlParseResult.Success)?.draft
            ?: error("expected success but was $result")

    private fun tag(id: Long, name: String, parentId: Long? = null) = Tag(
        id = id,
        name = name,
        parentId = parentId,
        sortOrder = 0,
        icon = null,
        createdAt = 100L,
        updatedAt = 200L,
    )

    /** [NLTransactionParser] 手写 fake：可控返回与异常注入，记录 generate 入参。 */
    private class FakeNlParser : NLTransactionParser {
        var available: Boolean = true
        var generateResult: String? = "{}"
        var generateThrows: Throwable? = null
        val generateCalls = mutableListOf<Pair<String, String>>()

        override suspend fun isAvailable(): Boolean = available

        override suspend fun generate(systemInstruction: String, userText: String): String? {
            generateCalls += systemInstruction to userText
            generateThrows?.let { throw it }
            return generateResult
        }
    }

    /** [TagRepository] 手写 fake：按 parentId 返回预置子节点。 */
    private class FakeTagRepository : TagRepository {
        val childrenByParent = mutableMapOf<Long?, List<Tag>>()

        override fun observeChildren(parentId: Long?): Flow<List<Tag>> = flowOf(emptyList())

        override suspend fun getChildren(parentId: Long?): List<Tag> =
            childrenByParent[parentId] ?: emptyList()

        override suspend fun getRecentUsedTags(sinceEpochMillis: Long, limit: Int): List<Tag> =
            emptyList()

        override suspend fun updateSortOrder(tags: List<Tag>) = Unit

        override suspend fun addSubTag(parentId: Long, name: String, icon: String?): Long = 0L

        override suspend fun renameTag(tagId: Long, newName: String) = Unit

        override suspend fun getDeleteImpact(tagId: Long): TagDeleteImpact =
            TagDeleteImpact(0, 0, emptyList())

        override suspend fun deleteTag(tagId: Long) = Unit
    }
}
