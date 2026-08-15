package com.expfal.yunayu.ui.screen.quickadd

import com.expfal.yunayu.domain.model.CategoryExpense
import com.expfal.yunayu.domain.model.RecentTransaction
import com.expfal.yunayu.domain.model.Tag
import com.expfal.yunayu.domain.model.TagDeleteImpact
import com.expfal.yunayu.domain.model.Transaction
import com.expfal.yunayu.domain.model.TransactionType
import com.expfal.yunayu.domain.model.WindowTotals
import com.expfal.yunayu.domain.nl.NLTransactionParser
import com.expfal.yunayu.domain.nl.ParseNaturalLanguageTransactionUseCase
import com.expfal.yunayu.domain.nl.model.NlParseFailure
import com.expfal.yunayu.domain.repository.TagRepository
import com.expfal.yunayu.domain.repository.TransactionRepository
import com.expfal.yunayu.domain.usecase.AddParsedTransactionUseCase
import com.expfal.yunayu.domain.usecase.AddTransactionUseCase
import com.expfal.yunayu.domain.usecase.GetRecentCategoriesUseCase
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.AfterEachCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.api.extension.RegisterExtension

/** [QuickAddViewModel] 的 JVM 单元测试（手写 fake UseCase/仓储 + coroutines-test）。 */
@OptIn(ExperimentalCoroutinesApi::class)
class QuickAddViewModelTest {

    @JvmField
    @RegisterExtension
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `parses amount text to cents`() {
        assertNull(QuickAddViewModel.parseAmountToCents(""))
        assertNull(QuickAddViewModel.parseAmountToCents("0"))
        assertNull(QuickAddViewModel.parseAmountToCents("."))
        assertNull(QuickAddViewModel.parseAmountToCents("0."))
        assertEquals(1250L, QuickAddViewModel.parseAmountToCents("12.5"))
        assertEquals(5L, QuickAddViewModel.parseAmountToCents("0.05"))
        assertNull(QuickAddViewModel.parseAmountToCents("12.345"))
        assertNull(QuickAddViewModel.parseAmountToCents("12345678901234567890"))
    }

    @Test
    fun `loads suggestions and preselects first tag`() = runTest {
        val tagRepo = FakeTagRepository().apply {
            recentTags = listOf(tag(1L, "学习"), tag(2L, "社交"))
        }
        val viewModel = viewModel(tagRepo, FakeTransactionRepository())

        assertEquals(listOf(tag(1L, "学习"), tag(2L, "社交")), viewModel.uiState.value.suggestedTags)
        assertEquals(1L, viewModel.uiState.value.selectedTagId)
    }

    @Test
    fun `selectedTagId is null when suggestions are empty`() = runTest {
        val tagRepo = FakeTagRepository().apply {
            recentTags = emptyList()
            rootTags = emptyList()
        }
        val viewModel = viewModel(tagRepo, FakeTransactionRepository())

        assertTrue(viewModel.uiState.value.suggestedTags.isEmpty())
        assertNull(viewModel.uiState.value.selectedTagId)
    }

    @Test
    fun `amount above threshold requests confirmation without saving`() = runTest {
        val txRepo = FakeTransactionRepository()
        val viewModel = viewModel(FakeTagRepository(), txRepo)

        viewModel.onDigit('1')
        viewModel.onDigit('5')
        viewModel.onDigit('0')
        viewModel.onSave()

        assertTrue(viewModel.uiState.value.confirmRequested)
        assertEquals(0, txRepo.added.size)
    }

    @Test
    fun `confirms and saves with Saved event`() = runTest {
        val txRepo = FakeTransactionRepository().apply { nextId = 42L }
        val tagRepo = FakeTagRepository().apply { recentTags = listOf(tag(1L, "学习")) }
        val viewModel = viewModel(tagRepo, txRepo)

        val events = mutableListOf<QuickAddEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.events.collect { events.add(it) }
        }

        viewModel.onDigit('1')
        viewModel.onDigit('5')
        viewModel.onDigit('0')
        viewModel.onSave()
        assertTrue(viewModel.uiState.value.confirmRequested)
        assertEquals(0, txRepo.added.size)

        viewModel.onConfirmNecessary()

        assertEquals(1, txRepo.added.size)
        assertEquals(15000L, txRepo.added.single().amountCents)
        assertEquals(1L, txRepo.added.single().tagId)
        assertEquals(listOf(QuickAddEvent.Saved), events)
        assertEquals("", viewModel.uiState.value.amountText)
        assertFalse(viewModel.uiState.value.confirmRequested)
    }

    @Test
    fun `saves income transaction when type switched`() = runTest {
        val txRepo = FakeTransactionRepository()
        val viewModel = viewModel(FakeTagRepository(), txRepo)

        viewModel.setType(TransactionType.INCOME)
        viewModel.onDigit('5')
        viewModel.onSave()
        runCurrent()

        assertEquals(1, txRepo.added.size)
        assertEquals(TransactionType.INCOME, txRepo.added.single().type)
        assertEquals(500L, txRepo.added.single().amountCents)
    }

    @Test
    fun `setType is ignored while saving`() = runTest {
        val gate = CompletableDeferred<Long>()
        val txRepo = FakeTransactionRepository().apply { addGate = gate }
        val viewModel = viewModel(FakeTagRepository(), txRepo)

        viewModel.onDigit('1')
        viewModel.onSave()

        assertTrue(viewModel.uiState.value.saving)

        viewModel.setType(TransactionType.INCOME)
        assertEquals(TransactionType.EXPENSE, viewModel.uiState.value.transactionType)

        gate.complete(7L)
        runCurrent()
        assertFalse(viewModel.uiState.value.saving)
    }

    @Test
    fun `resetForOpen resets transactionType to expense`() = runTest {
        val viewModel = viewModel(FakeTagRepository(), FakeTransactionRepository())

        viewModel.setType(TransactionType.INCOME)
        assertEquals(TransactionType.INCOME, viewModel.uiState.value.transactionType)

        viewModel.resetForOpen()
        assertEquals(TransactionType.EXPENSE, viewModel.uiState.value.transactionType)
    }

    @Test
    fun `setNlMode resets transactionType to expense`() = runTest {
        val viewModel = viewModel(FakeTagRepository(), FakeTransactionRepository())

        viewModel.setType(TransactionType.INCOME)
        assertEquals(TransactionType.INCOME, viewModel.uiState.value.transactionType)

        viewModel.setNlMode(true)
        assertEquals(TransactionType.EXPENSE, viewModel.uiState.value.transactionType)
    }

    @Test
    fun `income large amount confirms then saves as income`() = runTest {
        val txRepo = FakeTransactionRepository()
        val viewModel = viewModel(FakeTagRepository(), txRepo)

        viewModel.setType(TransactionType.INCOME)
        viewModel.onDigit('1')
        viewModel.onDigit('5')
        viewModel.onDigit('0')
        viewModel.onSave()

        assertTrue(viewModel.uiState.value.confirmRequested)
        assertEquals(0, txRepo.added.size)

        viewModel.onConfirmNecessary()
        runCurrent()

        assertEquals(1, txRepo.added.size)
        assertEquals(TransactionType.INCOME, txRepo.added.single().type)
        assertEquals(15000L, txRepo.added.single().amountCents)
    }

    @Test
    fun `ignores repeated save while saving`() = runTest {
        val gate = CompletableDeferred<Long>()
        val txRepo = FakeTransactionRepository().apply { addGate = gate }
        val viewModel = viewModel(FakeTagRepository(), txRepo)

        viewModel.onDigit('1')
        viewModel.onDigit('2')
        viewModel.onSave()

        assertTrue(viewModel.uiState.value.saving)
        assertEquals(1, txRepo.added.size)

        viewModel.onSave()
        assertEquals(1, txRepo.added.size)

        gate.complete(7L)
        runCurrent()

        assertFalse(viewModel.uiState.value.saving)
        assertEquals(1, txRepo.added.size)
    }

    @Test
    fun `dismissConfirm clears confirmation without saving`() = runTest {
        val txRepo = FakeTransactionRepository()
        val viewModel = viewModel(FakeTagRepository(), txRepo)

        viewModel.onDigit('2')
        viewModel.onDigit('0')
        viewModel.onDigit('0')
        viewModel.onSave()
        assertTrue(viewModel.uiState.value.confirmRequested)

        viewModel.onDismissConfirm()

        assertFalse(viewModel.uiState.value.confirmRequested)
        assertEquals(0, txRepo.added.size)
    }

    @Test
    fun `digit input enforces integer and fraction limits`() = runTest {
        val viewModel = viewModel(FakeTagRepository(), FakeTransactionRepository())

        repeat(7) { viewModel.onDigit('9') }
        viewModel.onDigit('1')
        assertEquals("9999999", viewModel.uiState.value.amountText)

        repeat(7) { viewModel.onDelete() }
        viewModel.onDigit('.')
        viewModel.onDigit('5')
        viewModel.onDigit('0')
        viewModel.onDigit('1')
        viewModel.onDigit('.')
        assertEquals("0.50", viewModel.uiState.value.amountText)
    }

    @Test
    fun `emits SaveFailed and keeps input when save fails`() = runTest {
        val txRepo = FakeTransactionRepository().apply { addError = RuntimeException("db down") }
        val tagRepo = FakeTagRepository().apply { recentTags = listOf(tag(1L, "学习")) }
        val viewModel = viewModel(tagRepo, txRepo)

        val events = mutableListOf<QuickAddEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.events.collect { events.add(it) }
        }

        viewModel.onDigit('5')
        viewModel.onSave()

        assertEquals(listOf(QuickAddEvent.SaveFailed), events)
        assertFalse(viewModel.uiState.value.saving)
        assertTrue(viewModel.uiState.value.saveFailed)
        assertEquals("5", viewModel.uiState.value.amountText)
    }

    @Test
    fun `sets saving synchronously when save starts`() = runTest {
        val gate = CompletableDeferred<Long>()
        val txRepo = FakeTransactionRepository().apply { addGate = gate }
        val viewModel = viewModel(FakeTagRepository(), txRepo)

        viewModel.onDigit('1')
        viewModel.onSave()

        assertTrue(viewModel.uiState.value.saving)
        assertEquals(1, txRepo.added.size)

        gate.complete(7L)
        runCurrent()
        assertFalse(viewModel.uiState.value.saving)
    }

    @Test
    fun `refreshSuggestedTags reloads suggestions`() = runTest {
        val tagRepo = FakeTagRepository().apply {
            recentTags = listOf(tag(1L, "学习"))
        }
        val viewModel = viewModel(tagRepo, FakeTransactionRepository())

        assertEquals(listOf(tag(1L, "学习")), viewModel.uiState.value.suggestedTags)

        tagRepo.recentTags = listOf(tag(2L, "社交"), tag(3L, "生活"))
        viewModel.refreshSuggestedTags()
        runCurrent()

        assertEquals(listOf(tag(2L, "社交"), tag(3L, "生活")), viewModel.uiState.value.suggestedTags)
        assertEquals(2L, viewModel.uiState.value.selectedTagId)
    }

    @Test
    fun `falls back to root tags when recent categories load fails`() = runTest {
        val tagRepo = FakeTagRepository().apply {
            recentError = RuntimeException("db down")
            rootTags = listOf(tag(1L, "学习"), tag(2L, "社交"))
        }
        val viewModel = viewModel(tagRepo, FakeTransactionRepository())

        assertEquals(listOf(tag(1L, "学习"), tag(2L, "社交")), viewModel.uiState.value.suggestedTags)
        assertEquals(1L, viewModel.uiState.value.selectedTagId)
    }

    @Test
    fun `builds root name mapping for sub tag display`() = runTest {
        val tagRepo = FakeTagRepository().apply {
            recentTags = listOf(tag(5L, "教材", parentId = 1L))
            rootTags = listOf(tag(1L, "学习"), tag(2L, "社交"))
        }
        val viewModel = viewModel(tagRepo, FakeTransactionRepository())

        assertEquals(mapOf(1L to "学习", 2L to "社交"), viewModel.uiState.value.rootNameById)
    }

    @Test
    fun `loadAllTags groups children by root`() = runTest {
        val tagRepo = FakeTagRepository().apply {
            rootTags = listOf(tag(1L, "学习"), tag(2L, "社交"))
            childrenByParent = mapOf(
                1L to listOf(tag(5L, "教材", parentId = 1L)),
                2L to listOf(tag(6L, "社团", parentId = 2L)),
            )
        }
        val viewModel = viewModel(tagRepo, FakeTransactionRepository())

        viewModel.loadAllTags()
        runCurrent()

        val mapping = viewModel.uiState.value.allTagsByRoot
        assertEquals(2, mapping.size)
        assertEquals(listOf(tag(5L, "教材", parentId = 1L)), mapping[tag(1L, "学习")])
        assertEquals(listOf(tag(6L, "社团", parentId = 2L)), mapping[tag(2L, "社交")])
    }

    @Test
    fun `save failure refreshes suggested tags with latest data`() = runTest {
        val txRepo = FakeTransactionRepository().apply { addError = RuntimeException("db down") }
        val tagRepo = FakeTagRepository().apply { recentTags = listOf(tag(1L, "学习")) }
        val viewModel = viewModel(tagRepo, txRepo)
        runCurrent()
        assertEquals(1L, viewModel.uiState.value.selectedTagId)

        tagRepo.recentTags = listOf(tag(2L, "社交"))
        viewModel.onDigit('5')
        viewModel.onSave()
        runCurrent()

        assertTrue(viewModel.uiState.value.saveFailed)
        assertEquals(listOf(tag(2L, "社交")), viewModel.uiState.value.suggestedTags)
        assertEquals(2L, viewModel.uiState.value.selectedTagId)
    }

    @Test
    fun `parses NL text into draft and syncs matched tagId to selection`() = runTest {
        val tagRepo = FakeTagRepository().apply {
            rootTags = listOf(tag(1L, "学习"), tag(2L, "生活"))
            childrenByParent = mapOf(2L to listOf(tag(11L, "餐饮", parentId = 2L)))
        }
        val nlParser = FakeNlParser().apply {
            generateResult = "{\"amount\":\"20\",\"tag\":\"生活·餐饮\",\"note\":\"午饭\"}"
        }
        val viewModel = viewModel(tagRepo, FakeTransactionRepository(), nlParser)

        viewModel.setNlMode(true)
        viewModel.onNlInputChange("午饭20")
        viewModel.onParseNl()
        runCurrent()

        val state = viewModel.uiState.value
        assertNull(state.nlFailure)
        assertEquals(2000L, state.nlDraft?.amountCents)
        assertEquals("午饭", state.nlDraft?.note)
        assertEquals(11L, state.selectedTagId)
    }

    @Test
    fun `sets failure state when NL output is malformed`() = runTest {
        val nlParser = FakeNlParser().apply { generateResult = "没有 JSON 输出" }
        val viewModel = viewModel(FakeTagRepository(), FakeTransactionRepository(), nlParser)

        viewModel.setNlMode(true)
        viewModel.onNlInputChange("午饭20")
        viewModel.onParseNl()
        runCurrent()

        assertEquals(NlParseFailure.MALFORMED_OUTPUT, viewModel.uiState.value.nlFailure)
        assertNull(viewModel.uiState.value.nlDraft)
    }

    @Test
    fun `sets ENGINE_UNAVAILABLE when parser unavailable`() = runTest {
        val nlParser = FakeNlParser().apply { available = false }
        val viewModel = viewModel(FakeTagRepository(), FakeTransactionRepository(), nlParser)

        viewModel.setNlMode(true)
        viewModel.onNlInputChange("午饭20")
        viewModel.onParseNl()
        runCurrent()

        assertEquals(NlParseFailure.ENGINE_UNAVAILABLE, viewModel.uiState.value.nlFailure)
    }

    @Test
    fun `saves parsed draft preserving note type and occurredAt then emits Saved`() = runTest {
        val txRepo = FakeTransactionRepository().apply { nextId = 99L }
        val tagRepo = FakeTagRepository().apply { rootTags = listOf(tag(1L, "学习")) }
        val nlParser = FakeNlParser().apply {
            generateResult = "{\"amount\":\"15.5\",\"tag\":\"学习\",\"note\":\"午饭\"}"
        }
        val viewModel = viewModel(tagRepo, txRepo, nlParser)

        val events = mutableListOf<QuickAddEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.events.collect { events.add(it) }
        }

        viewModel.setNlMode(true)
        viewModel.onNlInputChange("午饭15.5")
        viewModel.onParseNl()
        runCurrent()
        viewModel.onSaveNl()
        runCurrent()

        val saved = txRepo.added.single()
        assertEquals(1550L, saved.amountCents)
        assertEquals(TransactionType.EXPENSE, saved.type)
        assertEquals("午饭", saved.note)
        assertEquals(1L, saved.tagId)
        assertTrue(saved.occurredAt > 0L)
        assertEquals(listOf(QuickAddEvent.Saved), events)
    }

    @Test
    fun `NL large amount requests confirmation then saves via confirm`() = runTest {
        val txRepo = FakeTransactionRepository()
        val tagRepo = FakeTagRepository().apply { rootTags = listOf(tag(1L, "学习")) }
        val nlParser = FakeNlParser().apply {
            generateResult = "{\"amount\":\"150\",\"tag\":\"学习\"}"
        }
        val viewModel = viewModel(tagRepo, txRepo, nlParser)

        viewModel.setNlMode(true)
        viewModel.onNlInputChange("买书150")
        viewModel.onParseNl()
        runCurrent()

        viewModel.onSaveNl()
        assertTrue(viewModel.uiState.value.confirmRequested)
        assertEquals(0, txRepo.added.size)

        viewModel.onConfirmNecessary()
        runCurrent()

        assertEquals(1, txRepo.added.size)
        assertEquals(15000L, txRepo.added.single().amountCents)
        assertEquals(1L, txRepo.added.single().tagId)
    }

    @Test
    fun `NL unmatched tag phrase does not leak preselected tag into save`() = runTest {
        val txRepo = FakeTransactionRepository()
        val tagRepo = FakeTagRepository().apply {
            recentTags = listOf(tag(1L, "学习"))
            rootTags = listOf(tag(1L, "学习"))
        }
        val nlParser = FakeNlParser().apply {
            generateResult = "{\"amount\":\"20\",\"tag\":\"不存在·标签\"}"
        }
        val viewModel = viewModel(tagRepo, txRepo, nlParser)

        viewModel.setNlMode(true)
        viewModel.onNlInputChange("午饭20")
        viewModel.onParseNl()
        runCurrent()

        assertNull(viewModel.uiState.value.nlDraft?.tagId)
        assertNull(viewModel.uiState.value.nlTagId)
        assertEquals(1L, viewModel.uiState.value.selectedTagId)

        viewModel.onSaveNl()
        runCurrent()

        assertEquals(1, txRepo.added.size)
        assertNull(txRepo.added.single().tagId)
    }

    @Test
    fun `NL unmatched tag phrase uses user picked tag after correction`() = runTest {
        val txRepo = FakeTransactionRepository()
        val tagRepo = FakeTagRepository().apply {
            recentTags = listOf(tag(1L, "学习"), tag(2L, "社交"))
            rootTags = listOf(tag(1L, "学习"), tag(2L, "社交"))
        }
        val nlParser = FakeNlParser().apply {
            generateResult = "{\"amount\":\"20\",\"tag\":\"不存在·标签\"}"
        }
        val viewModel = viewModel(tagRepo, txRepo, nlParser)

        viewModel.setNlMode(true)
        viewModel.onNlInputChange("午饭20")
        viewModel.onParseNl()
        runCurrent()

        viewModel.onSelectTag(2L)
        assertEquals(2L, viewModel.uiState.value.nlTagId)

        viewModel.onSaveNl()
        runCurrent()

        assertEquals(1, txRepo.added.size)
        assertEquals(2L, txRepo.added.single().tagId)
    }

    @Test
    fun `sets NO_AMOUNT failure when NL output has JSON but no amount`() = runTest {
        val nlParser = FakeNlParser().apply {
            generateResult = "{\"tag\":\"学习\",\"note\":\"午饭\"}"
        }
        val viewModel = viewModel(FakeTagRepository(), FakeTransactionRepository(), nlParser)

        viewModel.setNlMode(true)
        viewModel.onNlInputChange("午饭")
        viewModel.onParseNl()
        runCurrent()

        assertEquals(NlParseFailure.NO_AMOUNT, viewModel.uiState.value.nlFailure)
        assertNull(viewModel.uiState.value.nlDraft)
    }

    @Test
    fun `NL save failure emits SaveFailed keeps draft and clears confirm`() = runTest {
        val txRepo = FakeTransactionRepository().apply { addError = RuntimeException("db down") }
        val tagRepo = FakeTagRepository().apply { rootTags = listOf(tag(1L, "学习")) }
        val nlParser = FakeNlParser().apply {
            generateResult = "{\"amount\":\"150\",\"tag\":\"学习\"}"
        }
        val viewModel = viewModel(tagRepo, txRepo, nlParser)

        val events = mutableListOf<QuickAddEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.events.collect { events.add(it) }
        }

        viewModel.setNlMode(true)
        viewModel.onNlInputChange("买书150")
        viewModel.onParseNl()
        runCurrent()

        viewModel.onSaveNl()
        assertTrue(viewModel.uiState.value.confirmRequested)
        viewModel.onConfirmNecessary()
        runCurrent()

        assertEquals(listOf(QuickAddEvent.SaveFailed), events)
        assertTrue(viewModel.uiState.value.saveFailed)
        assertFalse(viewModel.uiState.value.confirmRequested)
        assertNotNull(viewModel.uiState.value.nlDraft)
    }

    @Test
    fun `resetForOpen clears stale amount and NL state`() = runTest {
        val tagRepo = FakeTagRepository().apply { rootTags = listOf(tag(1L, "学习")) }
        val nlParser = FakeNlParser().apply {
            generateResult = "{\"amount\":\"20\",\"tag\":\"学习\"}"
        }
        val viewModel = viewModel(tagRepo, FakeTransactionRepository(), nlParser)

        viewModel.onDigit('5')
        viewModel.setNlMode(true)
        viewModel.onNlInputChange("午饭20")
        viewModel.onParseNl()
        runCurrent()
        assertNotNull(viewModel.uiState.value.nlDraft)

        viewModel.resetForOpen()

        val state = viewModel.uiState.value
        assertEquals("", state.amountText)
        assertFalse(state.nlMode)
        assertEquals("", state.nlInputText)
        assertNull(state.nlDraft)
        assertNull(state.nlFailure)
        assertNull(state.nlTagId)
        assertFalse(state.confirmRequested)
        assertFalse(state.saveFailed)
    }

    private fun viewModel(
        tagRepo: TagRepository,
        txRepo: TransactionRepository,
        nlParser: NLTransactionParser = FakeNlParser(),
    ) = QuickAddViewModel(
        tagRepository = tagRepo,
        getRecentCategoriesUseCase = GetRecentCategoriesUseCase(tagRepo),
        addTransactionUseCase = AddTransactionUseCase(txRepo),
        parseNaturalLanguageTransactionUseCase = ParseNaturalLanguageTransactionUseCase(nlParser, tagRepo),
        addParsedTransactionUseCase = AddParsedTransactionUseCase(txRepo),
    )

    private fun tag(id: Long, name: String, parentId: Long? = null) = Tag(id = id, name = name, parentId = parentId)

    /** [TagRepository] 手写 fake：返回预置的最近/根标签，可配置异常模拟加载失败。 */
    private class FakeTagRepository : TagRepository {

        var recentTags: List<Tag> = emptyList()
        var rootTags: List<Tag> = emptyList()
        var childrenByParent: Map<Long, List<Tag>> = emptyMap()
        var recentError: Throwable? = null

        override fun observeChildren(parentId: Long?): Flow<List<Tag>> = flowOf(emptyList())

        override suspend fun getChildren(parentId: Long?): List<Tag> =
            if (parentId == null) rootTags else childrenByParent[parentId] ?: emptyList()

        override suspend fun getRecentUsedTags(sinceEpochMillis: Long, limit: Int): List<Tag> {
            recentError?.let { throw it }
            return recentTags
        }

        override suspend fun updateSortOrder(tags: List<Tag>) = Unit

        override suspend fun addSubTag(parentId: Long, name: String, icon: String?): Long = 0L

        override suspend fun renameTag(tagId: Long, newName: String) = Unit

        override suspend fun getDeleteImpact(tagId: Long): TagDeleteImpact = TagDeleteImpact(0, 0, emptyList())

        override suspend fun deleteTag(tagId: Long) = Unit
    }

    /** [TransactionRepository] 手写 fake：记录 add 入参，可选经 [addGate] 挂起模拟慢写或抛错。 */
    private class FakeTransactionRepository : TransactionRepository {

        val added = mutableListOf<Transaction>()
        var nextId: Long = 0L
        var addGate: CompletableDeferred<Long>? = null
        var addError: Throwable? = null

        override suspend fun add(transaction: Transaction): Long {
            addError?.let { throw it }
            added += transaction
            addGate?.let { return it.await() }
            return nextId
        }

        override fun observeAll(): Flow<List<Transaction>> = flowOf(emptyList())

        override fun observeByTag(tagId: Long): Flow<List<Transaction>> = flowOf(emptyList())

        override fun observeExpenseSumBetween(startInclusiveMs: Long, endExclusiveMs: Long): Flow<Long> = flowOf(0L)

        override fun observeHeldCents(): Flow<Long> = flowOf(0L)

        override suspend fun getWindowTotals(
            startInclusiveMs: Long,
            endExclusiveMs: Long,
        ): WindowTotals = WindowTotals(0L, 0L)

        override suspend fun getExpenseByCategory(
            startInclusiveMs: Long,
            endExclusiveMs: Long,
        ): List<CategoryExpense> = emptyList()

        override fun observeRecent(limit: Int): Flow<List<RecentTransaction>> = flowOf(emptyList())
    }

    /** [NLTransactionParser] 手写 fake：可控可用性与返回，用于 NL 解析路径。 */
    private class FakeNlParser : NLTransactionParser {
        var available: Boolean = true
        var generateResult: String? = "{}"
        var generateThrows: Throwable? = null

        override suspend fun isAvailable(): Boolean = available

        override suspend fun generate(systemInstruction: String, userText: String): String? {
            generateThrows?.let { throw it }
            return generateResult
        }
    }
}

/** 将 [Dispatchers.Main] 替换为测试调度器，使 [androidx.lifecycle.viewModelScope] 可运行于 JVM 单测。 */
@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(
    private val testDispatcher: TestDispatcher = UnconfinedTestDispatcher(),
) : BeforeEachCallback, AfterEachCallback {

    override fun beforeEach(context: ExtensionContext?) {
        Dispatchers.setMain(testDispatcher)
    }

    override fun afterEach(context: ExtensionContext?) {
        Dispatchers.resetMain()
    }
}
