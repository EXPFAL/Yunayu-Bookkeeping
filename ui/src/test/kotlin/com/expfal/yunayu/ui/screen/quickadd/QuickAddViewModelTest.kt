package com.expfal.yunayu.ui.screen.quickadd

import com.expfal.yunayu.domain.model.Tag
import com.expfal.yunayu.domain.model.Transaction
import com.expfal.yunayu.domain.repository.TagRepository
import com.expfal.yunayu.domain.repository.TransactionRepository
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

    private fun viewModel(
        tagRepo: TagRepository,
        txRepo: TransactionRepository,
    ) = QuickAddViewModel(
        getRecentCategoriesUseCase = GetRecentCategoriesUseCase(tagRepo),
        addTransactionUseCase = AddTransactionUseCase(txRepo),
    )

    private fun tag(id: Long, name: String) = Tag(id = id, name = name)

    /** [TagRepository] 手写 fake：返回预置的最近/根标签。 */
    private class FakeTagRepository : TagRepository {

        var recentTags: List<Tag> = emptyList()
        var rootTags: List<Tag> = emptyList()

        override fun observeChildren(parentId: Long?): Flow<List<Tag>> = flowOf(emptyList())

        override suspend fun getChildren(parentId: Long?): List<Tag> = rootTags

        override suspend fun getRecentUsedTags(sinceEpochMillis: Long, limit: Int): List<Tag> = recentTags

        override suspend fun updateSortOrder(tags: List<Tag>) = Unit
    }

    /** [TransactionRepository] 手写 fake：记录 add 入参，可选经 [addGate] 挂起以模拟慢写。 */
    private class FakeTransactionRepository : TransactionRepository {

        val added = mutableListOf<Transaction>()
        var nextId: Long = 0L
        var addGate: CompletableDeferred<Long>? = null

        override suspend fun add(transaction: Transaction): Long {
            added += transaction
            addGate?.let { return it.await() }
            return nextId
        }

        override fun observeAll(): Flow<List<Transaction>> = flowOf(emptyList())

        override fun observeByTag(tagId: Long): Flow<List<Transaction>> = flowOf(emptyList())
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
