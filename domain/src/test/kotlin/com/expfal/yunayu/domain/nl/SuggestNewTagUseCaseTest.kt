package com.expfal.yunayu.domain.nl

import com.expfal.yunayu.domain.model.Tag
import com.expfal.yunayu.domain.nl.model.TagSuggestion
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/** [SuggestNewTagUseCase] 的 JVM 单元测试（手写 fake 引擎）。 */
class SuggestNewTagUseCaseTest {

    private val candidates = listOf(tag(1L, "生活"), tag(2L, "学习"))

    @Test
    fun `suggests tag name and root on matched output`() = runTest {
        val parser = FakeNlParser().apply {
            generateResult = "{\"tag_name\":\"咖啡\",\"root\":\"生活\"}"
        }
        val useCase = SuggestNewTagUseCase(parser)

        assertEquals(TagSuggestion("咖啡", "生活"), useCase("每天买咖啡", candidates))
    }

    @Test
    fun `returns null when root not in candidates`() = runTest {
        val parser = FakeNlParser().apply {
            generateResult = "{\"tag_name\":\"咖啡\",\"root\":\"娱乐\"}"
        }
        val useCase = SuggestNewTagUseCase(parser)

        assertNull(useCase("每天买咖啡", candidates))
    }

    @Test
    fun `returns null when engine unavailable`() = runTest {
        val parser = FakeNlParser().apply { available = false }
        val useCase = SuggestNewTagUseCase(parser)

        assertNull(useCase("每天买咖啡", candidates))
    }

    @Test
    fun `returns null when generate returns null`() = runTest {
        val parser = FakeNlParser().apply { generateResult = null }
        val useCase = SuggestNewTagUseCase(parser)

        assertNull(useCase("每天买咖啡", candidates))
    }

    @Test
    fun `returns null for malformed output`() = runTest {
        val parser = FakeNlParser().apply { generateResult = "没有任何 JSON" }
        val useCase = SuggestNewTagUseCase(parser)

        assertNull(useCase("每天买咖啡", candidates))
    }

    @Test
    fun `returns null for blank input`() = runTest {
        val parser = FakeNlParser()
        val useCase = SuggestNewTagUseCase(parser)

        assertNull(useCase("   ", candidates))
    }

    private fun tag(id: Long, name: String) = Tag(id = id, name = name, parentId = null)

    /** [NLTransactionParser] 手写 fake：可控可用性与返回。 */
    private class FakeNlParser : NLTransactionParser {
        var available: Boolean = true
        var generateResult: String? = "{}"

        override suspend fun isAvailable(): Boolean = available

        override suspend fun generate(systemInstruction: String, userText: String): String? = generateResult
    }
}
