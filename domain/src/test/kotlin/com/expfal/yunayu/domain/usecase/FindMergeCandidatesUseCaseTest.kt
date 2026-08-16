package com.expfal.yunayu.domain.usecase

import com.expfal.yunayu.domain.model.MergeCandidate
import com.expfal.yunayu.domain.model.MergeDecision
import com.expfal.yunayu.domain.model.Tag
import com.expfal.yunayu.domain.model.TagDeleteImpact
import com.expfal.yunayu.domain.model.TransactionType
import com.expfal.yunayu.domain.nl.NLTransactionParser
import com.expfal.yunayu.domain.repository.TagRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** [FindMergeCandidatesUseCase] 的 JVM 单元测试（手写 fake 标签仓储 + 解析器）。 */
class FindMergeCandidatesUseCaseTest {

    @Test
    fun `does not call engine when total count not above threshold`() = runTest {
        val tags = FakeTagRepository().apply {
            childrenByParent[null] = listOf(tag(1L, "学习", null))
            childrenByParent[1L] = listOf(tag(2L, "教材", 1L), tag(3L, "考证", 1L))
            impactByTagId[2L] = TagDeleteImpact(1, 1, listOf("教材"))
            impactByTagId[3L] = TagDeleteImpact(1, 2, listOf("考证"))
        }
        val parser = FakeParser(available = true)
        val useCase = FindMergeCandidatesUseCase(tags, parser)

        val result = useCase()

        assertTrue(result.isEmpty())
        assertTrue(parser.generateCalls.isEmpty())
    }

    @Test
    fun `excludes root and tags with children from candidate pairs`() = runTest {
        val tags = FakeTagRepository().apply {
            childrenByParent[null] = listOf(tag(1L, "学习", null))
            childrenByParent[1L] = listOf(tag(2L, "教材", 1L), tag(3L, "考证", 1L))
            childrenByParent[2L] = listOf(tag(4L, "考研", 2L))
            impactByTagId[3L] = TagDeleteImpact(1, 4, listOf("考证"))
            impactByTagId[4L] = TagDeleteImpact(1, 3, listOf("考研"))
        }
        val parser = FakeParser(available = true).apply {
            generateResult = """[{"tag_a":"考证","tag_b":"考研","decision":"A_INTO_B"}]"""
        }
        val useCase = FindMergeCandidatesUseCase(tags, parser)

        val result = useCase()

        assertEquals(1, result.size)
        assertEquals("考证", result.single().tagA.name)
        assertEquals("考研", result.single().tagB.name)

        // 根「学习」与带子级「教材」均被排除，不应出现在请求提示词中
        val systemInstruction = parser.generateCalls.single().first
        assertTrue(!systemInstruction.contains("\"tag_a\":\"学习\""))
        assertTrue(!systemInstruction.contains("\"tag_a\":\"教材\""))
    }

    @Test
    fun `returns merge candidate with counts and decision`() = runTest {
        val tags = FakeTagRepository().apply {
            childrenByParent[null] = listOf(tag(1L, "学习", null))
            childrenByParent[1L] = listOf(tag(2L, "餐饮", 1L), tag(3L, "吃饭", 1L))
            impactByTagId[2L] = TagDeleteImpact(1, 4, listOf("餐饮"))
            impactByTagId[3L] = TagDeleteImpact(1, 3, listOf("吃饭"))
        }
        val parser = FakeParser(available = true).apply {
            generateResult = """[{"tag_a":"餐饮","tag_b":"吃饭","decision":"A_INTO_B"}]"""
        }
        val useCase = FindMergeCandidatesUseCase(tags, parser)

        val result = useCase()

        assertEquals(
            listOf(
                MergeCandidate(
                    tagA = tag(2L, "餐饮", 1L),
                    tagB = tag(3L, "吃饭", 1L),
                    countA = 4,
                    countB = 3,
                    decision = MergeDecision.MERGE_A_INTO_B,
                ),
            ),
            result,
        )
    }

    @Test
    fun `filters out keep_both decision`() = runTest {
        val tags = FakeTagRepository().apply {
            childrenByParent[null] = listOf(tag(1L, "学习", null))
            childrenByParent[1L] = listOf(tag(2L, "餐饮", 1L), tag(3L, "吃饭", 1L))
            impactByTagId[2L] = TagDeleteImpact(1, 4, listOf("餐饮"))
            impactByTagId[3L] = TagDeleteImpact(1, 3, listOf("吃饭"))
        }
        val parser = FakeParser(available = true).apply {
            generateResult = """[{"tag_a":"餐饮","tag_b":"吃饭","decision":"KEEP_BOTH"}]"""
        }
        val useCase = FindMergeCandidatesUseCase(tags, parser)

        val result = useCase()

        assertTrue(result.isEmpty())
    }

    @Test
    fun `prioritizes same-root pairs`() = runTest {
        val tags = FakeTagRepository().apply {
            childrenByParent[null] = listOf(tag(1L, "学习", null), tag(10L, "生活", null))
            childrenByParent[1L] = listOf(tag(2L, "教材", 1L), tag(3L, "考证", 1L))
            childrenByParent[10L] = listOf(tag(11L, "餐饮", 10L))
            impactByTagId[2L] = TagDeleteImpact(1, 2, listOf("教材"))
            impactByTagId[3L] = TagDeleteImpact(1, 2, listOf("考证"))
            impactByTagId[11L] = TagDeleteImpact(1, 100, listOf("餐饮"))
        }
        val parser = FakeParser(available = true)
        val useCase = FindMergeCandidatesUseCase(tags, parser)

        useCase()

        // 同根对（教材·考证）应排在跨根对（教材·餐饮）之前，尽管跨根对计数更大
        val systemInstruction = parser.generateCalls.single().first
        assertTrue(systemInstruction.indexOf("\"tag_a\":\"教材\"") < systemInstruction.indexOf("\"tag_a\":\"餐饮\""))
    }

    @Test
    fun `caps candidate pairs at thirty`() = runTest {
        val tags = FakeTagRepository().apply {
            childrenByParent[null] = listOf(tag(1L, "学习", null))
            val leaves = (2L..11L).map { tag(it, "标签$it", 1L) }
            childrenByParent[1L] = leaves
            leaves.forEach { impactByTagId[it.id] = TagDeleteImpact(1, 10, listOf(it.name)) }
        }
        val parser = FakeParser(available = true)
        val useCase = FindMergeCandidatesUseCase(tags, parser)

        useCase()

        // 10 个叶子可组合 45 对，但上限 30 对，按每批 15 对拆为 2 批
        assertEquals(2, parser.generateCalls.size)
    }

    @Test
    fun `returns empty when engine unavailable`() = runTest {
        val tags = FakeTagRepository().apply {
            childrenByParent[null] = listOf(tag(1L, "学习", null))
            childrenByParent[1L] = listOf(tag(2L, "餐饮", 1L), tag(3L, "吃饭", 1L))
            impactByTagId[2L] = TagDeleteImpact(1, 4, listOf("餐饮"))
            impactByTagId[3L] = TagDeleteImpact(1, 3, listOf("吃饭"))
        }
        val parser = FakeParser(available = false)
        val useCase = FindMergeCandidatesUseCase(tags, parser)

        val result = useCase()

        assertTrue(result.isEmpty())
        assertTrue(parser.generateCalls.isEmpty())
    }

    @Test
    fun `rethrows cancellation exception`() = runTest {
        val tags = FakeTagRepository().apply {
            childrenByParent[null] = listOf(tag(1L, "学习", null))
            childrenByParent[1L] = listOf(tag(2L, "餐饮", 1L), tag(3L, "吃饭", 1L))
            impactByTagId[2L] = TagDeleteImpact(1, 4, listOf("餐饮"))
            impactByTagId[3L] = TagDeleteImpact(1, 3, listOf("吃饭"))
        }
        val parser = FakeParser(available = true).apply { generateThrows = CancellationException("cancelled") }
        val useCase = FindMergeCandidatesUseCase(tags, parser)

        var caught: Throwable? = null
        try {
            useCase()
        } catch (throwable: Throwable) {
            caught = throwable
        }

        assertTrue(caught is CancellationException)
    }

    @Test
    fun `swallows non cancellation exception`() = runTest {
        val tags = FakeTagRepository().apply {
            childrenByParent[null] = listOf(tag(1L, "学习", null))
            childrenByParent[1L] = listOf(tag(2L, "餐饮", 1L), tag(3L, "吃饭", 1L))
            impactByTagId[2L] = TagDeleteImpact(1, 4, listOf("餐饮"))
            impactByTagId[3L] = TagDeleteImpact(1, 3, listOf("吃饭"))
        }
        val parser = FakeParser(available = true).apply { generateThrows = RuntimeException("boom") }
        val useCase = FindMergeCandidatesUseCase(tags, parser)

        val result = useCase()

        assertTrue(result.isEmpty())
    }

    private fun tag(id: Long, name: String, parentId: Long?) = Tag(id = id, name = name, parentId = parentId)

    /** [TagRepository] 手写 fake：按 parentId 返回预置子节点，按 tagId 返回影响面。 */
    private class FakeTagRepository : TagRepository {

        val childrenByParent = mutableMapOf<Long?, List<Tag>>()
        val impactByTagId = mutableMapOf<Long, TagDeleteImpact>()

        override fun observeChildren(parentId: Long?): Flow<List<Tag>> = flowOf(emptyList())

        override suspend fun getChildren(parentId: Long?): List<Tag> =
            childrenByParent[parentId] ?: emptyList()

        override suspend fun getRecentUsedTags(
            sinceEpochMillis: Long,
            type: TransactionType,
            limit: Int,
        ): List<Tag> = emptyList()

        override suspend fun updateSortOrder(tags: List<Tag>) = Unit

        override suspend fun addSubTag(parentId: Long, name: String, icon: String?): Long = 0L

        override suspend fun addRootTag(name: String, icon: String?): Long = 0L

        override suspend fun renameTag(tagId: Long, newName: String) = Unit

        override suspend fun getDeleteImpact(tagId: Long): TagDeleteImpact =
            impactByTagId[tagId] ?: TagDeleteImpact(0, 0, emptyList())

        override suspend fun deleteTag(tagId: Long) = Unit

        override suspend fun mergeTags(keepTagId: Long, dropTagId: Long) = Unit
    }

    /** [NLTransactionParser] 手写 fake：可控可用性、返回与异常。 */
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
}
