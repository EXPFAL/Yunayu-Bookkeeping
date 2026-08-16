package com.expfal.yunayu.domain.nl

import com.expfal.yunayu.domain.model.TransactionType
import com.expfal.yunayu.domain.nl.model.OrganizeRecord
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** [OrganizeSuggestUseCase] 的 JVM 单元测试（手写 fake 解析器 + coroutines-test）。 */
class OrganizeSuggestUseCaseTest {

    @Test
    fun `returns empty for empty records without calling engine`() = runTest {
        val parser = FakeParser(available = true)
        val useCase = OrganizeSuggestUseCase(parser)

        val result = useCase(emptyList(), listOf("学习"), "收入")

        assertTrue(result.isEmpty())
        assertTrue(parser.availableCalls.isEmpty())
        assertTrue(parser.generateCalls.isEmpty())
    }

    @Test
    fun `returns empty when engine unavailable`() = runTest {
        val parser = FakeParser(available = false)
        val useCase = OrganizeSuggestUseCase(parser)

        val result = useCase(listOf(record(1L)), listOf("学习"), "收入")

        assertTrue(result.isEmpty())
        assertTrue(parser.generateCalls.isEmpty())
    }

    @Test
    fun `returns empty when generate returns null`() = runTest {
        val parser = FakeParser(available = true).apply { generateResult = null }
        val useCase = OrganizeSuggestUseCase(parser)

        val result = useCase(listOf(record(1L)), listOf("学习"), "收入")

        assertTrue(result.isEmpty())
        assertEquals(1, parser.generateCalls.size)
    }

    @Test
    fun `parses suggestions from generate output`() = runTest {
        val parser = FakeParser(available = true).apply {
            generateResult = """[{"record_id":1,"action":"ATTACH","tag_name":"学习"}]"""
        }
        val useCase = OrganizeSuggestUseCase(parser)

        val result = useCase(listOf(record(1L)), listOf("学习"), "收入")

        assertEquals(1, result.size)
        assertEquals(1L, result.single().recordId)
    }

    @Test
    fun `rethrows cancellation exception`() = runTest {
        val parser = FakeParser(available = true).apply { generateThrows = CancellationException("cancelled") }
        val useCase = OrganizeSuggestUseCase(parser)

        var caught: Throwable? = null
        try {
            useCase(listOf(record(1L)), listOf("学习"), "收入")
        } catch (throwable: Throwable) {
            caught = throwable
        }

        assertTrue(caught is CancellationException)
    }

    @Test
    fun `swallows non cancellation exception`() = runTest {
        val parser = FakeParser(available = true).apply { generateThrows = RuntimeException("boom") }
        val useCase = OrganizeSuggestUseCase(parser)

        val result = useCase(listOf(record(1L)), listOf("学习"), "收入")

        assertTrue(result.isEmpty())
    }

    private fun record(id: Long) = OrganizeRecord(id, "买书", 2_500L, TransactionType.EXPENSE, 900L)

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
