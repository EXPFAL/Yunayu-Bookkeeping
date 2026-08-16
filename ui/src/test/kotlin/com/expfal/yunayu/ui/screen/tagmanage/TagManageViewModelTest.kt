package com.expfal.yunayu.ui.screen.tagmanage

import com.expfal.yunayu.domain.model.CategoryExpense
import com.expfal.yunayu.domain.model.DuplicateTagNameException
import com.expfal.yunayu.domain.model.MergeDecision
import com.expfal.yunayu.domain.model.RecentTransaction
import com.expfal.yunayu.domain.model.Tag
import com.expfal.yunayu.domain.model.TagDeleteImpact
import com.expfal.yunayu.domain.model.Transaction
import com.expfal.yunayu.domain.model.TransactionType
import com.expfal.yunayu.domain.model.WindowTotals
import com.expfal.yunayu.domain.nl.NLTransactionParser
import com.expfal.yunayu.domain.report.model.Report
import com.expfal.yunayu.domain.report.model.ReportPeriodType
import com.expfal.yunayu.domain.repository.ReportRepository
import com.expfal.yunayu.domain.repository.TagRepository
import com.expfal.yunayu.domain.repository.TransactionRepository
import com.expfal.yunayu.domain.usecase.FindMergeCandidatesUseCase
import com.expfal.yunayu.domain.usecase.MergeTagsUseCase
import com.expfal.yunayu.ui.screen.quickadd.MainDispatcherRule
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

/** [TagManageViewModel] 的 JVM 单元测试（手写 fake 仓储 + coroutines-test）。 */
@OptIn(ExperimentalCoroutinesApi::class)
class TagManageViewModelTest {

    @JvmField
    @RegisterExtension
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `loads four root partitions`() = runTest {
        val viewModel = viewModel(fourRootRepo())
        runCurrent()

        val state = viewModel.uiState.value
        assertFalse(state.loading)
        assertEquals(listOf(1L, 2L, 3L, 4L), state.roots.map { it.id })
        assertEquals(setOf(1L, 2L, 3L, 4L), state.childrenByRoot.keys)
    }

    @Test
    fun `addSubTag success clears error`() = runTest {
        val repo = fourRootRepo()
        val viewModel = viewModel(repo)
        runCurrent()

        viewModel.addSubTag(1L, "教材")
        runCurrent()

        assertEquals(listOf(Triple(1L, "教材", null)), repo.added)
        assertNull(viewModel.uiState.value.errorMessage)
        assertFalse(viewModel.uiState.value.busy)
    }

    @Test
    fun `addSubTag duplicate name maps friendly error`() = runTest {
        val repo = fourRootRepo().apply { addError = DuplicateTagNameException("dup") }
        val viewModel = viewModel(repo)
        runCurrent()

        viewModel.addSubTag(1L, "教材")
        runCurrent()

        assertEquals("同名标签已存在", viewModel.uiState.value.errorMessage)
        assertFalse(viewModel.uiState.value.busy)
    }

    @Test
    fun `addSubTag illegal argument maps repository message`() = runTest {
        val repo = fourRootRepo().apply { addError = IllegalArgumentException("标签名不可为空") }
        val viewModel = viewModel(repo)
        runCurrent()

        viewModel.addSubTag(1L, "")
        runCurrent()

        assertEquals("标签名不可为空", viewModel.uiState.value.errorMessage)
    }

    @Test
    fun `rename succeeds and closes rename state`() = runTest {
        val repo = fourRootRepo()
        val viewModel = viewModel(repo)
        runCurrent()

        viewModel.requestRename(tag(5, "旧名", parentId = 1L))
        viewModel.rename(5, "新名")
        runCurrent()

        assertEquals(listOf(5L to "新名"), repo.renamed)
        assertNull(viewModel.uiState.value.renamingTag)
    }

    @Test
    fun `rename duplicate name keeps rename state with error`() = runTest {
        val repo = fourRootRepo().apply { renameError = DuplicateTagNameException("dup") }
        val viewModel = viewModel(repo)
        runCurrent()

        viewModel.requestRename(tag(5, "旧名", parentId = 1L))
        viewModel.rename(5, "教材")
        runCurrent()

        assertEquals("同名标签已存在", viewModel.uiState.value.errorMessage)
        assertNotNull(viewModel.uiState.value.renamingTag)
    }

    @Test
    fun `requestDelete computes impact into pendingDelete`() = runTest {
        val repo = fourRootRepo().apply { impactResult = TagDeleteImpact(3, 5, listOf("a", "b", "c")) }
        val viewModel = viewModel(repo)
        runCurrent()

        val child = tag(5, "教材", parentId = 1L)
        viewModel.requestDelete(child)
        runCurrent()

        assertEquals(child, viewModel.uiState.value.pendingDelete?.first)
        assertEquals(3, viewModel.uiState.value.pendingDelete?.second?.subtreeNodeCount)
        assertEquals(5, viewModel.uiState.value.pendingDelete?.second?.affectedTransactionCount)
    }

    @Test
    fun `confirmDelete deletes clears state and emits Deleted`() = runTest {
        val repo = fourRootRepo()
        val viewModel = viewModel(repo)
        runCurrent()

        val events = mutableListOf<TagManageEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.events.collect { events.add(it) }
        }

        viewModel.requestDelete(tag(5, "教材", parentId = 1L))
        runCurrent()
        viewModel.confirmDelete()
        runCurrent()

        assertEquals(listOf(5L), repo.deletedIds)
        assertNull(viewModel.uiState.value.pendingDelete)
        assertEquals(listOf(TagManageEvent.Deleted), events)
    }

    @Test
    fun `cancelDelete clears pendingDelete`() = runTest {
        val viewModel = viewModel(fourRootRepo())
        runCurrent()

        viewModel.requestDelete(tag(5, "教材", parentId = 1L))
        runCurrent()
        viewModel.cancelDelete()

        assertNull(viewModel.uiState.value.pendingDelete)
    }

    @Test
    fun `onReorder optimistically updates then rolls back on failure`() = runTest {
        val repo = fourRootRepo().apply {
            childrenFlows[1L] = MutableStateFlow(listOf(tag(11, "a", 1L), tag(12, "b", 1L)))
        }
        val viewModel = viewModel(repo)
        runCurrent()

        val original = viewModel.uiState.value.childrenByRoot.getValue(1L)
        val gate = CompletableDeferred<Unit>()
        repo.sortGate = gate

        val reordered = listOf(tag(12, "b", 1L), tag(11, "a", 1L))
        viewModel.onReorder(1L, reordered)

        assertEquals(reordered, viewModel.uiState.value.childrenByRoot.getValue(1L))

        gate.completeExceptionally(RuntimeException("db down"))
        runCurrent()

        assertEquals(original, viewModel.uiState.value.childrenByRoot.getValue(1L))
        assertEquals("排序保存失败", viewModel.uiState.value.errorMessage)
    }

    @Test
    fun `onReorder success keeps new order`() = runTest {
        val repo = fourRootRepo().apply {
            childrenFlows[1L] = MutableStateFlow(listOf(tag(11, "a", 1L), tag(12, "b", 1L)))
        }
        val viewModel = viewModel(repo)
        runCurrent()

        val reordered = listOf(tag(12, "b", 1L), tag(11, "a", 1L))
        viewModel.onReorder(1L, reordered)
        runCurrent()

        assertEquals(reordered, viewModel.uiState.value.childrenByRoot.getValue(1L))
        assertFalse(viewModel.uiState.value.busy)
    }

    @Test
    fun `onReorder success converges with observation flow pushing new order`() = runTest {
        val repo = fourRootRepo().apply {
            childrenFlows[1L] = MutableStateFlow(listOf(tag(11, "a", 1L), tag(12, "b", 1L)))
        }
        val viewModel = viewModel(repo)
        runCurrent()

        val reordered = listOf(tag(12, "b", 1L), tag(11, "a", 1L))
        viewModel.onReorder(1L, reordered)
        runCurrent()

        // 模拟持久化成功后观察链重发射新序，最终态应与提交的新序一致
        repo.childrenFlows.getValue(1L).value = reordered
        runCurrent()

        assertEquals(reordered, viewModel.uiState.value.childrenByRoot.getValue(1L))
        assertFalse(viewModel.uiState.value.busy)
    }

    @Test
    fun `detectMergeCandidates writes candidates on success`() = runTest {
        val repo = mergeCandidateRepo()
        val parser = FakeParser(available = true).apply {
            generateResult = """[{"tag_a":"教材","tag_b":"教材费","decision":"A_INTO_B"}]"""
        }
        val viewModel = viewModel(repo, parser = parser)
        runCurrent()

        viewModel.detectMergeCandidates()
        runCurrent()

        val state = viewModel.uiState.value
        assertFalse(state.mergeDetecting)
        assertFalse(state.mergeDetectFailed)
        assertEquals(1, state.mergeCandidates.size)
        val candidate = state.mergeCandidates.single()
        assertEquals(11L, candidate.tagA.id)
        assertEquals(12L, candidate.tagB.id)
        assertEquals(MergeDecision.MERGE_A_INTO_B, candidate.decision)
    }

    @Test
    fun `detectMergeCandidates degrades to empty when engine unavailable`() = runTest {
        val repo = mergeCandidateRepo()
        val viewModel = viewModel(repo, parser = FakeParser(available = false))
        runCurrent()

        viewModel.detectMergeCandidates()
        runCurrent()

        val state = viewModel.uiState.value
        assertFalse(state.mergeDetecting)
        assertTrue(state.mergeDetectFailed)
        assertTrue(state.mergeCandidates.isEmpty())
    }

    @Test
    fun `setMergeChoice records chosen direction`() = runTest {
        val repo = mergeCandidateRepo()
        val parser = FakeParser(available = true).apply {
            generateResult = """[{"tag_a":"教材","tag_b":"教材费","decision":"A_INTO_B"}]"""
        }
        val viewModel = viewModel(repo, parser = parser)
        runCurrent()
        viewModel.detectMergeCandidates()
        runCurrent()

        val candidate = viewModel.uiState.value.mergeCandidates.single()
        viewModel.setMergeChoice(candidate, MergeChoice.B_INTO_A)
        assertEquals(MergeChoice.B_INTO_A, viewModel.uiState.value.mergeChoices["11:12"])
        viewModel.setMergeChoice(candidate, MergeChoice.KEEP_BOTH)
        assertEquals(MergeChoice.KEEP_BOTH, viewModel.uiState.value.mergeChoices["11:12"])
    }

    @Test
    fun `confirmMerge success removes candidate and emits Merged`() = runTest {
        val repo = mergeCandidateRepo()
        val parser = FakeParser(available = true).apply {
            generateResult = """[{"tag_a":"教材","tag_b":"教材费","decision":"A_INTO_B"}]"""
        }
        val txRepo = FakeTransactionRepository().apply { occurredAts = listOf(100L, 200L) }
        val viewModel = viewModel(repo, parser = parser, txRepo = txRepo)
        runCurrent()
        viewModel.detectMergeCandidates()
        runCurrent()

        val events = mutableListOf<TagManageEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.events.collect { events.add(it) }
        }

        val candidate = viewModel.uiState.value.mergeCandidates.single()
        viewModel.setMergeChoice(candidate, MergeChoice.A_INTO_B)
        viewModel.confirmMerge(candidate)
        runCurrent()

        assertEquals(listOf(12L to 11L), repo.mergedCalls)
        assertTrue(viewModel.uiState.value.mergeCandidates.isEmpty())
        assertEquals(
            listOf(TagManageEvent.Merged(affectedTransactionCount = 2, keepTagName = "教材费")),
            events,
        )
        assertFalse(viewModel.uiState.value.merging)
    }

    @Test
    fun `confirmMerge validation failure emits MergeFailed without crash`() = runTest {
        val repo = mergeCandidateRepo().apply { mergeError = IllegalArgumentException("根标签不可合并") }
        val parser = FakeParser(available = true).apply {
            generateResult = """[{"tag_a":"教材","tag_b":"教材费","decision":"A_INTO_B"}]"""
        }
        val viewModel = viewModel(repo, parser = parser)
        runCurrent()
        viewModel.detectMergeCandidates()
        runCurrent()

        val events = mutableListOf<TagManageEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.events.collect { events.add(it) }
        }

        val candidate = viewModel.uiState.value.mergeCandidates.single()
        viewModel.setMergeChoice(candidate, MergeChoice.B_INTO_A)
        viewModel.confirmMerge(candidate)
        runCurrent()

        assertEquals(listOf(TagManageEvent.MergeFailed), events)
        assertEquals(1, viewModel.uiState.value.mergeCandidates.size)
        assertFalse(viewModel.uiState.value.merging)
        assertTrue(viewModel.uiState.value.mergeFailed)
    }

    @Test
    fun `confirmMerge busy guard prevents concurrent merges`() = runTest {
        val repo = mergeCandidateRepo()
        val gate = CompletableDeferred<Unit>()
        val txRepo = FakeTransactionRepository().apply { occurredAtsGate = gate }
        val parser = FakeParser(available = true).apply {
            generateResult = """[{"tag_a":"教材","tag_b":"教材费","decision":"A_INTO_B"}]"""
        }
        val viewModel = viewModel(repo, parser = parser, txRepo = txRepo)
        runCurrent()
        viewModel.detectMergeCandidates()
        runCurrent()

        val candidate = viewModel.uiState.value.mergeCandidates.single()
        viewModel.setMergeChoice(candidate, MergeChoice.A_INTO_B)
        viewModel.confirmMerge(candidate)
        viewModel.confirmMerge(candidate)

        assertTrue(viewModel.uiState.value.merging)
        gate.complete(Unit)
        runCurrent()

        assertEquals(1, repo.mergedCalls.size)
        assertFalse(viewModel.uiState.value.merging)
    }

    private fun fourRootRepo(): FakeTagRepository {
        val repo = FakeTagRepository()
        val roots = listOf(tag(1, "学习"), tag(2, "社交"), tag(3, "生活"), tag(4, "娱乐"))
        repo.rootsFlow.value = roots
        roots.forEach { repo.childrenFlows[it.id] = MutableStateFlow(emptyList()) }
        return repo
    }

    private fun tag(id: Long, name: String, parentId: Long? = null) = Tag(id = id, name = name, parentId = parentId)

    /** [TagRepository] 手写 fake：以 MutableStateFlow 驱动树观察，记录变更入参并可配置异常/挂起。 */
    private class FakeTagRepository : TagRepository {

        val rootsFlow = MutableStateFlow<List<Tag>>(emptyList())
        val childrenFlows = mutableMapOf<Long, MutableStateFlow<List<Tag>>>()

        var addError: Throwable? = null
        var renameError: Throwable? = null
        var deleteImpactError: Throwable? = null
        var deleteError: Throwable? = null
        var sortGate: CompletableDeferred<Unit>? = null
        var sortError: Throwable? = null
        var mergeError: Throwable? = null

        val added = mutableListOf<Triple<Long, String, String?>>()
        val renamed = mutableListOf<Pair<Long, String>>()
        val deletedIds = mutableListOf<Long>()
        val mergedCalls = mutableListOf<Pair<Long, Long>>()
        var impactResult = TagDeleteImpact(1, 0, emptyList())
        var lookupRoots: List<Tag> = emptyList()
        var lookupChildren: Map<Long, List<Tag>> = emptyMap()
        var impactByTagId: Map<Long, TagDeleteImpact> = emptyMap()

        override fun observeChildren(parentId: Long?): Flow<List<Tag>> =
            if (parentId == null) rootsFlow else childrenFlows.getOrPut(parentId) { MutableStateFlow(emptyList()) }

        override suspend fun getChildren(parentId: Long?): List<Tag> =
            if (parentId == null) lookupRoots else lookupChildren[parentId] ?: emptyList()

        override suspend fun getRecentUsedTags(sinceEpochMillis: Long, type: TransactionType, limit: Int): List<Tag> = emptyList()

        override suspend fun updateSortOrder(tags: List<Tag>) {
            sortError?.let { throw it }
            sortGate?.let { it.await() }
        }

        override suspend fun addSubTag(parentId: Long, name: String, icon: String?): Long {
            addError?.let { throw it }
            added += Triple(parentId, name, icon)
            return 100L
        }

        override suspend fun addRootTag(name: String, icon: String?): Long = 0L

        override suspend fun renameTag(tagId: Long, newName: String) {
            renameError?.let { throw it }
            renamed += tagId to newName
        }

        override suspend fun getDeleteImpact(tagId: Long): TagDeleteImpact {
            deleteImpactError?.let { throw it }
            return impactByTagId[tagId] ?: impactResult
        }

        override suspend fun deleteTag(tagId: Long) {
            deleteError?.let { throw it }
            deletedIds += tagId
        }

        override suspend fun mergeTags(keepTagId: Long, dropTagId: Long) {
            mergeError?.let { throw it }
            mergedCalls += keepTagId to dropTagId
        }
    }

    private fun viewModel(
        repo: TagRepository,
        parser: NLTransactionParser = FakeParser(),
        txRepo: TransactionRepository = FakeTransactionRepository(),
        reportRepo: ReportRepository = FakeReportRepository(),
    ) = TagManageViewModel(
        tagRepository = repo,
        findMergeCandidatesUseCase = FindMergeCandidatesUseCase(repo, parser),
        mergeTagsUseCase = MergeTagsUseCase(repo, txRepo, reportRepo),
        parser = parser,
    )

    private fun mergeCandidateRepo(): FakeTagRepository {
        val repo = fourRootRepo()
        repo.lookupRoots = listOf(tag(1, "学习"))
        repo.lookupChildren = mapOf(1L to listOf(tag(11, "教材", 1L), tag(12, "教材费", 1L)))
        repo.impactByTagId = mapOf(
            11L to TagDeleteImpact(1, 5, listOf("教材")),
            12L to TagDeleteImpact(1, 3, listOf("教材费")),
        )
        return repo
    }

    /** [NLTransactionParser] 手写 fake：可控可用性、返回与异常。 */
    private class FakeParser(var available: Boolean = true) : NLTransactionParser {
        var generateResult: String? = null
        var generateThrows: Throwable? = null

        override suspend fun isAvailable(): Boolean = available

        override suspend fun generate(systemInstruction: String, userText: String): String? {
            generateThrows?.let { throw it }
            return generateResult
        }
    }

    /** [TransactionRepository] 手写 fake：仅承载合并前的 occurredAt 快照。 */
    private class FakeTransactionRepository : TransactionRepository {
        var occurredAts: List<Long> = emptyList()
        var occurredAtsGate: CompletableDeferred<Unit>? = null
        var occurredAtsError: Throwable? = null

        override suspend fun add(transaction: Transaction): Long = 0L

        override suspend fun delete(transactionId: Long) = Unit

        override fun observeAll(): Flow<List<Transaction>> = flowOf(emptyList())

        override fun observeByTag(tagId: Long): Flow<List<Transaction>> = flowOf(emptyList())

        override fun observeExpenseSumBetween(startInclusiveMs: Long, endExclusiveMs: Long): Flow<Long> = flowOf(0L)

        override fun observeRecent(limit: Int): Flow<List<RecentTransaction>> = flowOf(emptyList())

        override fun observeHeldCents(): Flow<Long> = flowOf(0L)

        override suspend fun getWindowTotals(startInclusiveMs: Long, endExclusiveMs: Long): WindowTotals = WindowTotals(0L, 0L)

        override suspend fun getExpenseByCategory(
            startInclusiveMs: Long,
            endExclusiveMs: Long,
        ): List<CategoryExpense> = emptyList()

        override fun observeFiltered(
            startInclusiveMs: Long?,
            endExclusiveMs: Long?,
            tagIds: List<Long>,
            noteKeyword: String?,
        ): Flow<List<RecentTransaction>> = flowOf(emptyList())

        override fun observeUncategorizedCount(): Flow<Int> = flowOf(0)

        override suspend fun getUncategorized(): List<RecentTransaction> = emptyList()

        override suspend fun assignTags(assignments: Map<Long, List<Long>>) = Unit

        override suspend fun getOccurredAtsByTagIds(tagIds: List<Long>): List<Long> {
            occurredAtsError?.let { throw it }
            occurredAtsGate?.let { it.await() }
            return occurredAts
        }
    }

    /** [ReportRepository] 手写 fake：合并标脏无副作用。 */
    private class FakeReportRepository : ReportRepository {
        override fun observeByType(type: ReportPeriodType): Flow<List<Report>> = flowOf(emptyList())

        override suspend fun getByKey(periodType: ReportPeriodType, periodKey: String): Report? = null

        override suspend fun upsert(report: Report) = Unit

        override suspend fun invalidateWhereWindowContains(epochMillis: Long) = Unit
    }
}
