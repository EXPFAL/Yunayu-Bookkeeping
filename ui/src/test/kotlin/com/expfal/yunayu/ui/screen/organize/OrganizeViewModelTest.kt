package com.expfal.yunayu.ui.screen.organize

import com.expfal.yunayu.domain.model.AccountFilter
import com.expfal.yunayu.domain.model.CategoryExpense
import com.expfal.yunayu.domain.model.DuplicateTagNameException
import com.expfal.yunayu.domain.model.RecentTransaction
import com.expfal.yunayu.domain.model.Tag
import com.expfal.yunayu.domain.model.TagDeleteImpact
import com.expfal.yunayu.domain.model.Transaction
import com.expfal.yunayu.domain.model.TransactionType
import com.expfal.yunayu.domain.model.WindowTotals
import com.expfal.yunayu.domain.nl.NLTransactionParser
import com.expfal.yunayu.domain.nl.OrganizeSuggestUseCase
import com.expfal.yunayu.domain.report.model.Report
import com.expfal.yunayu.domain.report.model.ReportPeriodType
import com.expfal.yunayu.domain.repository.ReportRepository
import com.expfal.yunayu.domain.repository.TagRepository
import com.expfal.yunayu.domain.repository.TransactionRepository
import com.expfal.yunayu.domain.usecase.ApplyOrganizeUseCase
import com.expfal.yunayu.domain.usecase.FindMergeCandidatesUseCase
import com.expfal.yunayu.ui.screen.quickadd.MainDispatcherRule
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

/** [OrganizeViewModel] 的 JVM 单元测试（手写 fake 仓储/引擎 + coroutines-test）。 */
@OptIn(ExperimentalCoroutinesApi::class)
class OrganizeViewModelTest {

    @JvmField
    @RegisterExtension
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `empty uncategorized yields DONE with zero applied`() = runTest {
        val txRepo = FakeTransactionRepository().apply { uncategorized = emptyList() }
        val parser = FakeParser(available = true)
        val vm = viewModel(txRepo, parser = parser)

        vm.start()

        assertEquals(OrganizePhase.DONE, vm.uiState.value.phase)
        assertEquals(0, vm.uiState.value.totalRecords)
        assertEquals(0, vm.uiState.value.appliedCount)
        assertTrue(parser.availableCalls.isEmpty())
        assertTrue(parser.generateCalls.isEmpty())
    }

    @Test
    fun `unavailable engine yields ERROR_NO_API without calling generate`() = runTest {
        val txRepo = FakeTransactionRepository().apply {
            uncategorized = listOf(record(1000L))
        }
        val parser = FakeParser(available = false)
        val vm = viewModel(txRepo, parser = parser)

        vm.start()

        assertEquals(OrganizePhase.ERROR_NO_API, vm.uiState.value.phase)
        assertEquals(1, vm.uiState.value.totalRecords)
        assertTrue(parser.generateCalls.isEmpty())
        assertEquals(1, parser.availableCalls.size)
    }

    @Test
    fun `thirty records split into two batches`() = runTest {
        val txRepo = FakeTransactionRepository().apply {
            uncategorized = (1000L until 1030L).map { record(it) }
        }
        val parser = FakeParser(available = true)
        val vm = viewModel(txRepo, parser = parser)

        vm.start()

        assertEquals(OrganizePhase.REVIEWING, vm.uiState.value.phase)
        assertEquals(30, vm.uiState.value.totalRecords)
        assertEquals(2, vm.uiState.value.totalBatches)
        assertEquals(2, vm.uiState.value.doneBatches)
        assertEquals(30, vm.uiState.value.suggestions.size)
        assertEquals(2, parser.generateCalls.size)
        assertEquals(25, recordIdsIn(parser.generateCalls[0].first).size)
        assertEquals(5, recordIdsIn(parser.generateCalls[1].first).size)
    }

    @Test
    fun `accept modify reject update per item decision`() = runTest {
        val txRepo = FakeTransactionRepository().apply {
            uncategorized = listOf(record(1000L))
        }
        val vm = viewModel(txRepo)

        vm.start()

        assertEquals(OrganizeDecision.ACCEPT, vm.uiState.value.suggestions.single().decision)

        vm.setDecision(1000L, OrganizeDecision.REJECT)
        assertEquals(OrganizeDecision.REJECT, vm.uiState.value.suggestions.single().decision)

        vm.modifyTarget(1000L, 11L, "学习·教材")
        val modified = vm.uiState.value.suggestions.single()
        assertEquals(OrganizeDecision.MODIFY, modified.decision)
        assertEquals(11L, modified.modifiedTagId)
        assertEquals("学习·教材", modified.modifiedTagName)

        vm.setDecision(1000L, OrganizeDecision.ACCEPT)
        assertEquals(OrganizeDecision.ACCEPT, vm.uiState.value.suggestions.single().decision)
    }

    @Test
    fun `apply turns MODIFY into ATTACH and forwards result`() = runTest {
        val tagRepo = FakeTagRepository().apply {
            roots = listOf(tag(10L, "学习"))
            childrenByParent = mapOf(10L to listOf(tag(11L, "教材")))
        }
        val reportRepo = FakeReportRepository()
        val txRepo = FakeTransactionRepository().apply {
            uncategorized = listOf(record(1000L, occurredAt = 5_000L))
        }
        val vm = viewModel(txRepo, tagRepo, reportRepo)

        vm.start()
        vm.modifyTarget(1000L, 11L, "学习·教材")
        vm.apply()

        assertEquals(OrganizePhase.DONE, vm.uiState.value.phase)
        assertEquals(1, vm.uiState.value.appliedCount)
        assertTrue(vm.uiState.value.failedRecordIds.isEmpty())
        assertEquals(listOf(mapOf(11L to listOf(1000L))), txRepo.assignTagsCalls)
        assertEquals(listOf(5_000L), reportRepo.invalidated)
    }

    @Test
    fun `failed records retry re-suggests only failed ids`() = runTest {
        val tagRepo = FakeTagRepository().apply { roots = listOf(tag(10L, "生活")) }
        val txRepo = FakeTransactionRepository().apply {
            uncategorized = listOf(record(1000L))
        }
        val vm = viewModel(txRepo, tagRepo)

        vm.start()
        vm.apply()

        assertEquals(OrganizePhase.DONE, vm.uiState.value.phase)
        assertEquals(0, vm.uiState.value.appliedCount)
        assertEquals(listOf(1000L), vm.uiState.value.failedRecordIds)

        vm.retryFailed()

        assertEquals(OrganizePhase.REVIEWING, vm.uiState.value.phase)
        assertEquals(1, vm.uiState.value.totalRecords)
        assertEquals(1, vm.uiState.value.suggestions.size)
        assertTrue(vm.uiState.value.failedRecordIds.isEmpty())
    }

    @Test
    fun `cancel interrupts suggestion job`() = runTest {
        val parser = FakeParser(available = true).apply { generateGate = CompletableDeferred() }
        val txRepo = FakeTransactionRepository().apply {
            uncategorized = listOf(record(1000L))
        }
        val vm = viewModel(txRepo, parser = parser)

        vm.start()

        assertEquals(OrganizePhase.SUGGESTING, vm.uiState.value.phase)

        vm.cancel()

        assertEquals(OrganizePhase.IDLE, vm.uiState.value.phase)
    }

    @Test
    fun `done with applied records triggers merge hint count`() = runTest {
        val tagRepo = FakeTagRepository().apply {
            roots = listOf(tag(10L, "学习"))
            childrenByParent = mapOf(10L to listOf(tag(11L, "教材", 10L), tag(12L, "教材费", 10L)))
            impactByTagId = mapOf(
                11L to TagDeleteImpact(1, 5, listOf("教材")),
                12L to TagDeleteImpact(1, 3, listOf("教材费")),
            )
        }
        val txRepo = FakeTransactionRepository().apply { uncategorized = listOf(record(1000L)) }
        val mergeParser = FakeParser(available = true).apply {
            generateResult = """[{"tag_a":"教材","tag_b":"教材费","decision":"A_INTO_B"}]"""
        }
        val vm = viewModel(txRepo, tagRepo, mergeParser = mergeParser)

        vm.start()
        vm.apply()

        assertEquals(OrganizePhase.DONE, vm.uiState.value.phase)
        assertEquals(1, vm.uiState.value.appliedCount)
        assertEquals(1, vm.uiState.value.mergeHintCount)
    }

    @Test
    fun `done merge hint failure stays silent`() = runTest {
        val tagRepo = FakeTagRepository().apply {
            roots = listOf(tag(10L, "学习"))
            childrenByParent = mapOf(10L to listOf(tag(11L, "教材", 10L), tag(12L, "教材费", 10L)))
            impactByTagId = mapOf(
                11L to TagDeleteImpact(1, 5, listOf("教材")),
                12L to TagDeleteImpact(1, 3, listOf("教材费")),
            )
        }
        val txRepo = FakeTransactionRepository().apply { uncategorized = listOf(record(1000L)) }
        val mergeParser = FakeParser(available = true).apply { generateThrows = RuntimeException("boom") }
        val vm = viewModel(txRepo, tagRepo, mergeParser = mergeParser)

        vm.start()
        vm.apply()

        assertEquals(OrganizePhase.DONE, vm.uiState.value.phase)
        assertEquals(0, vm.uiState.value.mergeHintCount)
    }

    @Test
    fun `done carries reused tag names when duplicate create reused`() = runTest {
        val tagRepo = FakeTagRepository().apply {
            roots = listOf(tag(10L, "学习"))
            childrenByParent = mapOf(10L to listOf(tag(5L, "考证", 10L)))
            addSubTagError = DuplicateTagNameException("dup")
        }
        val txRepo = FakeTransactionRepository().apply { uncategorized = listOf(record(1000L)) }
        val parser = FakeParser(available = true).apply {
            generateResult = """[{"record_id":1000,"action":"CREATE","tag_name":"考证","root_name":"学习"}]"""
        }
        val vm = viewModel(txRepo, tagRepo, parser = parser)

        vm.start()
        vm.apply()

        assertEquals(OrganizePhase.DONE, vm.uiState.value.phase)
        assertEquals(1, vm.uiState.value.appliedCount)
        assertEquals(listOf("考证"), vm.uiState.value.reusedTagNames)
    }

    @Test
    fun `restart cancels stale merge hint detection`() = runTest {
        val tagRepo = FakeTagRepository().apply {
            roots = listOf(tag(10L, "学习"))
            childrenByParent = mapOf(10L to listOf(tag(11L, "教材", 10L), tag(12L, "教材费", 10L)))
            impactByTagId = mapOf(
                11L to TagDeleteImpact(1, 5, listOf("教材")),
                12L to TagDeleteImpact(1, 3, listOf("教材费")),
            )
        }
        val txRepo = FakeTransactionRepository().apply { uncategorized = listOf(record(1000L)) }
        val mergeParser = FakeParser(available = true).apply { generateGate = CompletableDeferred() }
        val vm = viewModel(txRepo, tagRepo, mergeParser = mergeParser)

        vm.start()
        vm.apply()

        assertEquals(OrganizePhase.DONE, vm.uiState.value.phase)

        // 旧整合检测挂起中；重启新会话应取消它，防陈旧 mergeHintCount 跨会话回写。
        vm.start()
        assertEquals(OrganizePhase.REVIEWING, vm.uiState.value.phase)

        mergeParser.generateGate?.complete("""[{"tag_a":"教材","tag_b":"教材费","decision":"A_INTO_B"}]""")

        assertEquals(0, vm.uiState.value.mergeHintCount)
    }

    private fun viewModel(
        txRepo: TransactionRepository,
        tagRepo: TagRepository = FakeTagRepository(),
        reportRepo: ReportRepository = FakeReportRepository(),
        parser: NLTransactionParser = FakeParser(),
        mergeParser: NLTransactionParser = FakeParser(),
    ) = OrganizeViewModel(
        transactionRepository = txRepo,
        tagRepository = tagRepo,
        organizeSuggestUseCase = OrganizeSuggestUseCase(parser),
        applyOrganizeUseCase = ApplyOrganizeUseCase(txRepo, tagRepo, reportRepo),
        findMergeCandidatesUseCase = FindMergeCandidatesUseCase(tagRepo, mergeParser),
        parser = parser,
    )

    private fun record(id: Long, occurredAt: Long = 0L) = RecentTransaction(
        id = id,
        amountCents = 1_000L,
        type = TransactionType.EXPENSE,
        tagName = null,
        occurredAt = occurredAt,
        note = "备注$id",
    )

    private fun tag(id: Long, name: String, parentId: Long? = null) = Tag(
        id = id,
        name = name,
        parentId = parentId,
    )

    /** 提取指令中的实际记录 id（过滤 few-shot 示例里的 1 与 2）。 */
    private fun recordIdsIn(instruction: String): List<Long> =
        Regex("\"record_id\":(\\d+)").findAll(instruction)
            .map { it.groupValues[1].toLong() }
            .filter { it >= 1_000L }
            .toList()

    /** [TransactionRepository] 手写 fake：驱动未分类快照并记录 assignTags 调用。 */
    private class FakeTransactionRepository : TransactionRepository {

        var uncategorized: List<RecentTransaction> = emptyList()
        val assignTagsCalls = mutableListOf<Map<Long, List<Long>>>()

        override suspend fun add(transaction: Transaction): Long = 0L

        override suspend fun delete(transactionId: Long) = Unit

        override fun observeAll(): Flow<List<Transaction>> = flowOf(emptyList())

        override fun observeByTag(tagId: Long): Flow<List<Transaction>> = flowOf(emptyList())

        override fun observeExpenseSumBetween(startInclusiveMs: Long, endExclusiveMs: Long): Flow<Long> = flowOf(0L)

        override fun observeRecent(limit: Int): Flow<List<RecentTransaction>> = flowOf(emptyList())

        override fun observeHeldCents(): Flow<Long> = flowOf(0L)

        override suspend fun getWindowTotals(startInclusiveMs: Long, endExclusiveMs: Long): WindowTotals =
            WindowTotals(0L, 0L)

        override suspend fun getExpenseByCategory(
            startInclusiveMs: Long,
            endExclusiveMs: Long,
        ): List<CategoryExpense> = emptyList()

        override fun observeFiltered(
            startInclusiveMs: Long?,
            endExclusiveMs: Long?,
            tagIds: List<Long>,
            noteKeyword: String?,
            accountFilter: AccountFilter,
        ): Flow<List<RecentTransaction>> = flowOf(emptyList())

        override fun observeUncategorizedCount(): Flow<Int> = flowOf(0)

        override suspend fun getUncategorized(): List<RecentTransaction> = uncategorized

        override suspend fun assignTags(assignments: Map<Long, List<Long>>) {
            assignTagsCalls += assignments
        }

        override suspend fun getOccurredAtsByTagIds(tagIds: List<Long>): List<Long> = emptyList()

        override suspend fun getById(id: Long): Transaction? = null

        override suspend fun updateTransaction(transaction: Transaction) = Unit
    }

    /** [TagRepository] 手写 fake：返回预置根 / 子标签。 */
    private class FakeTagRepository : TagRepository {

        var roots: List<Tag> = emptyList()
        var childrenByParent: Map<Long, List<Tag>> = emptyMap()
        var impactByTagId: Map<Long, TagDeleteImpact> = emptyMap()
        var addSubTagError: Throwable? = null

        override fun observeChildren(parentId: Long?): Flow<List<Tag>> = flowOf(emptyList())

        override suspend fun getChildren(parentId: Long?): List<Tag> =
            if (parentId == null) roots else childrenByParent[parentId] ?: emptyList()

        override suspend fun getRecentUsedTags(
            sinceEpochMillis: Long,
            type: TransactionType,
            limit: Int,
        ): List<Tag> = emptyList()

        override suspend fun updateSortOrder(tags: List<Tag>) = Unit

        override suspend fun addSubTag(parentId: Long, name: String, icon: String?): Long {
            addSubTagError?.let { throw it }
            return 0L
        }

        override suspend fun addRootTag(name: String, icon: String?): Long = 0L

        override suspend fun renameTag(tagId: Long, newName: String) = Unit

        override suspend fun getDeleteImpact(tagId: Long): TagDeleteImpact =
            impactByTagId[tagId] ?: TagDeleteImpact(0, 0, emptyList())

        override suspend fun deleteTag(tagId: Long) = Unit

        override suspend fun mergeTags(keepTagId: Long, dropTagId: Long) = Unit
    }

    /** [ReportRepository] 手写 fake：记录标脏调用。 */
    private class FakeReportRepository : ReportRepository {

        val invalidated = mutableListOf<Long>()

        override fun observeByType(type: ReportPeriodType): Flow<List<Report>> = flowOf(emptyList())

        override suspend fun getByKey(periodType: ReportPeriodType, periodKey: String): Report? = null

        override suspend fun upsert(report: Report) = Unit

        override suspend fun invalidateWhereWindowContains(epochMillis: Long) {
            invalidated += epochMillis
        }
    }

    /** [NLTransactionParser] 手写 fake：可控可用性、建议内容、异常与挂起门。 */
    private class FakeParser(var available: Boolean = true) : NLTransactionParser {

        var generateResult: String? = null
        var suggestionTagName: String = "学习"
        var generateThrows: Throwable? = null
        var generateGate: CompletableDeferred<String>? = null
        val availableCalls = mutableListOf<Boolean>()
        val generateCalls = mutableListOf<Pair<String, String>>()

        override suspend fun isAvailable(): Boolean {
            availableCalls += available
            return available
        }

        override suspend fun generate(systemInstruction: String, userText: String): String? {
            generateThrows?.let { throw it }
            generateCalls += systemInstruction to userText
            generateGate?.let { return it.await() }
            return generateResult ?: suggestionsFor(systemInstruction)
        }

        private fun suggestionsFor(instruction: String): String {
            val ids = Regex("\"record_id\":(\\d+)").findAll(instruction)
                .map { it.groupValues[1].toLong() }
                .toList()
            return ids.joinToString(prefix = "[", postfix = "]", separator = ",") { id ->
                """{"record_id":$id,"action":"ATTACH","tag_name":"$suggestionTagName"}"""
            }
        }
    }
}
