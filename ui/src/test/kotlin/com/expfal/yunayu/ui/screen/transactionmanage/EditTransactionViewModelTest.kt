package com.expfal.yunayu.ui.screen.transactionmanage

import com.expfal.yunayu.domain.model.Account
import com.expfal.yunayu.domain.model.AccountBalance
import com.expfal.yunayu.domain.model.AccountDeleteImpact
import com.expfal.yunayu.domain.model.AccountFilter
import com.expfal.yunayu.domain.model.CategoryExpense
import com.expfal.yunayu.domain.model.RecentTransaction
import com.expfal.yunayu.domain.model.Tag
import com.expfal.yunayu.domain.model.TagDeleteImpact
import com.expfal.yunayu.domain.model.Transaction
import com.expfal.yunayu.domain.model.TransactionType
import com.expfal.yunayu.domain.model.WindowTotals
import com.expfal.yunayu.domain.report.model.Report
import com.expfal.yunayu.domain.report.model.ReportPeriodType
import com.expfal.yunayu.domain.repository.AccountRepository
import com.expfal.yunayu.domain.repository.ReportRepository
import com.expfal.yunayu.domain.repository.TagRepository
import com.expfal.yunayu.domain.repository.TransactionRepository
import com.expfal.yunayu.domain.usecase.UpdateTransactionUseCase
import com.expfal.yunayu.ui.screen.quickadd.MainDispatcherRule
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

/** [EditTransactionViewModel] 的 JVM 单元测试（手写 fake 仓储 + coroutines-test）。 */
@OptIn(ExperimentalCoroutinesApi::class)
class EditTransactionViewModelTest {

    @JvmField
    @RegisterExtension
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `open prefills amount type note tag account and occurredAt`() = runTest {
        val txRepo = FakeTransactionRepository().apply {
            entityById = mapOf(
                42L to Transaction(
                    id = 42L,
                    amountCents = 1_234L,
                    type = TransactionType.INCOME,
                    note = "奖学金",
                    tagId = 5L,
                    accountId = 9L,
                    occurredAt = 900L,
                ),
            )
        }
        val tagRepo = FakeTagRepository().apply {
            rootTags = listOf(Tag(id = 1L, name = "学习"))
            childrenByParent = mapOf(1L to listOf(Tag(id = 5L, name = "教材", parentId = 1L)))
        }
        val accountRepo = FakeAccountRepository().apply {
            accounts = listOf(Account(id = 9L, name = "微信"))
        }
        val vm = viewModel(txRepo, accountRepo, tagRepo)

        vm.open(42L)
        runCurrent()

        val state = vm.uiState.value
        assertFalse(state.loading)
        assertFalse(state.loadFailed)
        assertEquals("12.34", state.amountText)
        assertEquals(TransactionType.INCOME, state.transactionType)
        assertEquals("奖学金", state.note)
        assertEquals(5L, state.selectedTagId)
        assertEquals("学习·教材", state.selectedTagName)
        assertEquals(9L, state.selectedAccountId)
        assertEquals(900L, state.occurredAt)
    }

    @Test
    fun `open marks loadFailed when transaction is missing`() = runTest {
        val vm = viewModel(FakeTransactionRepository(), FakeAccountRepository(), FakeTagRepository())

        vm.open(99L)
        runCurrent()

        assertFalse(vm.uiState.value.loading)
        assertTrue(vm.uiState.value.loadFailed)
    }

    @Test
    fun `stale load does not overwrite newer target`() = runTest {
        val gate = CompletableDeferred<Unit>()
        val txRepo = FakeTransactionRepository().apply {
            entityById = mapOf(
                1L to Transaction(id = 1L, amountCents = 100L, occurredAt = 1L),
                2L to Transaction(id = 2L, amountCents = 200L, occurredAt = 2L),
            )
            getByIdGate = gate
        }
        val vm = viewModel(txRepo, FakeAccountRepository(), FakeTagRepository())

        vm.open(1L)
        runCurrent()
        assertEquals(1L, vm.uiState.value.transactionId)

        vm.open(2L)
        runCurrent()
        assertEquals(2L, vm.uiState.value.transactionId)
        assertEquals("2", vm.uiState.value.amountText)

        // 释放旧目标 1 的迟到加载：目标已切换为 2，旧结果必须被丢弃
        gate.complete(Unit)
        runCurrent()

        assertEquals(2L, vm.uiState.value.transactionId)
        assertEquals("2", vm.uiState.value.amountText)
        assertEquals(2L, vm.uiState.value.occurredAt)
    }

    @Test
    fun `save updates via use case and emits Saved`() = runTest {
        val txRepo = FakeTransactionRepository().apply {
            entityById = mapOf(
                42L to Transaction(
                    id = 42L,
                    amountCents = 2_500L,
                    type = TransactionType.EXPENSE,
                    note = null,
                    tagId = 3L,
                    accountId = 9L,
                    occurredAt = 900L,
                ),
            )
        }
        val reportRepo = FakeReportRepository()
        val accountRepo = FakeAccountRepository().apply {
            accounts = listOf(Account(id = 9L, name = "微信"))
        }
        val tagRepo = FakeTagRepository().apply {
            rootTags = listOf(Tag(id = 1L, name = "学习"))
            childrenByParent = mapOf(1L to listOf(Tag(id = 3L, name = "教材", parentId = 1L)))
        }
        val vm = viewModel(txRepo, accountRepo, tagRepo, reportRepo)
        val events = mutableListOf<EditTransactionEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            vm.events.collect { events.add(it) }
        }

        vm.open(42L)
        runCurrent()
        vm.onSave()
        runCurrent()

        val updated = txRepo.updated.single()
        assertEquals(42L, updated.id)
        assertEquals(2_500L, updated.amountCents)
        assertEquals(TransactionType.EXPENSE, updated.type)
        assertNull(updated.note)
        assertEquals(3L, updated.tagId)
        assertEquals(9L, updated.accountId)
        assertEquals(900L, updated.occurredAt)
        assertEquals(listOf(900L), reportRepo.invalidated)
        assertEquals(listOf(EditTransactionEvent.Saved), events)
        assertFalse(vm.uiState.value.saving)
        assertFalse(vm.uiState.value.saveFailed)
    }

    @Test
    fun `save failure emits SaveFailed without invalidation`() = runTest {
        val txRepo = FakeTransactionRepository().apply {
            entityById = mapOf(
                42L to Transaction(id = 42L, amountCents = 2_500L, occurredAt = 900L),
            )
            updateError = IllegalStateException("db down")
        }
        val reportRepo = FakeReportRepository()
        val vm = viewModel(txRepo, FakeAccountRepository(), FakeTagRepository(), reportRepo)
        val events = mutableListOf<EditTransactionEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            vm.events.collect { events.add(it) }
        }

        vm.open(42L)
        runCurrent()
        vm.onSave()
        runCurrent()

        assertEquals(listOf(EditTransactionEvent.SaveFailed), events)
        assertTrue(vm.uiState.value.saveFailed)
        assertFalse(vm.uiState.value.saving)
        assertTrue(reportRepo.invalidated.isEmpty())
    }

    @Test
    fun `cancel produces no writes`() = runTest {
        val txRepo = FakeTransactionRepository().apply {
            entityById = mapOf(
                42L to Transaction(id = 42L, amountCents = 2_500L, occurredAt = 900L),
            )
        }
        val reportRepo = FakeReportRepository()
        val vm = viewModel(txRepo, FakeAccountRepository(), FakeTagRepository(), reportRepo)

        vm.open(42L)
        runCurrent()
        // 取消不调用任何保存路径，仅验证打开本身未产生写入。
        vm.onSelectTag(3L)
        vm.onSelectAccount(9L)
        vm.onNoteChange("改动后放弃")
        runCurrent()

        assertTrue(txRepo.updated.isEmpty())
        assertTrue(reportRepo.invalidated.isEmpty())
    }

    private fun viewModel(
        txRepo: TransactionRepository,
        accountRepo: AccountRepository = FakeAccountRepository(),
        tagRepo: TagRepository = FakeTagRepository(),
        reportRepo: ReportRepository = FakeReportRepository(),
    ) = EditTransactionViewModel(
        transactionRepository = txRepo,
        accountRepository = accountRepo,
        tagRepository = tagRepo,
        updateTransactionUseCase = UpdateTransactionUseCase(txRepo, reportRepo),
    )

    /** [TransactionRepository] 手写 fake：按主键返回交易，记录 updateTransaction 入参，可配置更新异常。 */
    private class FakeTransactionRepository : TransactionRepository {

        var entityById: Map<Long, Transaction> = emptyMap()
        val updated = mutableListOf<Transaction>()
        var updateError: Throwable? = null
        var getByIdGate: CompletableDeferred<Unit>? = null

        override suspend fun add(transaction: Transaction): Long = 0L

        override suspend fun delete(transactionId: Long) = Unit

        override suspend fun getById(id: Long): Transaction? {
            val gate = getByIdGate
            if (gate != null) {
                getByIdGate = null
                // 模拟旧加载无视取消、延迟返回，用于验证回写前的目标主键校验
                withContext(NonCancellable) { gate.await() }
            }
            return entityById[id]
        }

        override suspend fun updateTransaction(transaction: Transaction) {
            updateError?.let { throw it }
            updated += transaction
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

        override fun observeFiltered(
            startInclusiveMs: Long?,
            endExclusiveMs: Long?,
            tagIds: List<Long>,
            noteKeyword: String?,
            accountFilter: AccountFilter,
        ): Flow<List<RecentTransaction>> = flowOf(emptyList())

        override fun observeUncategorizedCount(): Flow<Int> = flowOf(0)

        override suspend fun getUncategorized(): List<RecentTransaction> = emptyList()

        override suspend fun assignTags(assignments: Map<Long, List<Long>>) = Unit

        override suspend fun getOccurredAtsByTagIds(tagIds: List<Long>): List<Long> = emptyList()
    }

    /** [AccountRepository] 手写 fake：一次性返回预置账户列表。 */
    private class FakeAccountRepository : AccountRepository {

        var accounts: List<Account> = emptyList()

        override fun observeAccounts(): Flow<List<Account>> = flowOf(emptyList())

        override suspend fun getAccounts(): List<Account> = accounts

        override fun observeBalances(): Flow<List<AccountBalance>> = flowOf(emptyList())

        override suspend fun addAccount(name: String): Long = 0L

        override suspend fun addAccount(name: String, initialBalanceCents: Long): Long = 0L

        override suspend fun renameAccount(id: Long, newName: String) = Unit

        override suspend fun updateAccount(id: Long, newName: String, initialBalanceCents: Long) = Unit

        override suspend fun getDeleteImpact(id: Long): AccountDeleteImpact = AccountDeleteImpact(0)

        override suspend fun deleteAccount(id: Long) = Unit

        override fun observeLastUsedAccountId(): Flow<Long?> = flowOf(null)

        override suspend fun saveLastUsedAccountId(id: Long?) = Unit

        override suspend fun updateInitialBalance(accountId: Long, cents: Long) = Unit
    }

    /** [TagRepository] 手写 fake：按 parentId 返回预置根 / 子标签。 */
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

        override suspend fun getDeleteImpact(tagId: Long): TagDeleteImpact = TagDeleteImpact(0, 0, emptyList())

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
}
