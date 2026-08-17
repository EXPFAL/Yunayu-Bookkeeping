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
import com.expfal.yunayu.domain.model.Transfer
import com.expfal.yunayu.domain.model.WindowTotals
import com.expfal.yunayu.domain.report.model.Report
import com.expfal.yunayu.domain.report.model.ReportPeriodType
import com.expfal.yunayu.domain.repository.AccountRepository
import com.expfal.yunayu.domain.repository.ReportRepository
import com.expfal.yunayu.domain.repository.TagRepository
import com.expfal.yunayu.domain.repository.TransactionRepository
import com.expfal.yunayu.domain.repository.TransferRepository
import com.expfal.yunayu.domain.usecase.DeleteTransactionUseCase
import com.expfal.yunayu.domain.util.TimeWindows
import com.expfal.yunayu.ui.screen.quickadd.MainDispatcherRule
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.time.LocalDate

/** [TransactionManageViewModel] 的 JVM 单元测试（手写 fake 仓储 + coroutines-test）。 */
@OptIn(ExperimentalCoroutinesApi::class)
class TransactionManageViewModelTest {

    @JvmField
    @RegisterExtension
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `initial state uses ALL with unbounded window and blank keyword normalized to null`() = runTest {
        val txRepo = FakeTransactionRepository()
        val vm = viewModel(txRepo)

        settle()

        assertEquals(TimeFilter.ALL, vm.uiState.value.timeRange)
        assertFalse(vm.uiState.value.loading)
        assertEquals(listOf(FilterArgs(null, null, emptyList(), null, AccountFilter.All)), txRepo.observeFilteredCalls)
    }

    @Test
    fun `last 7 days maps start to today minus six days`() = runTest {
        val txRepo = FakeTransactionRepository()
        val vm = viewModel(txRepo)
        settle()

        vm.selectTimeRange(TimeFilter.LAST_7_DAYS)
        runCurrent()

        val call = txRepo.observeFilteredCalls.last()
        assertEquals(TimeWindows.lastNDaysStartMillis(LocalDate.now(), 7), call.startInclusiveMs)
        assertNull(call.endExclusiveMs)
    }

    @Test
    fun `this month maps start to month start`() = runTest {
        val txRepo = FakeTransactionRepository()
        val vm = viewModel(txRepo)
        settle()

        vm.selectTimeRange(TimeFilter.THIS_MONTH)
        runCurrent()

        val call = txRepo.observeFilteredCalls.last()
        assertEquals(TimeWindows.monthStartMillis(LocalDate.now()), call.startInclusiveMs)
        assertNull(call.endExclusiveMs)
    }

    @Test
    fun `toggle tag selection passes selected ids and clearing resets`() = runTest {
        val txRepo = FakeTransactionRepository()
        val vm = viewModel(txRepo)
        settle()

        vm.toggleTagSelection(1L)
        vm.toggleTagSelection(2L)
        vm.toggleTagSelection(1L)
        runCurrent()

        assertEquals(listOf(2L), txRepo.observeFilteredCalls.last().tagIds)
        assertEquals(setOf(2L), vm.uiState.value.selectedTagIds)

        vm.clearTagSelection()
        runCurrent()

        assertEquals(emptyList<Long>(), txRepo.observeFilteredCalls.last().tagIds)
        assertTrue(vm.uiState.value.selectedTagIds.isEmpty())
    }

    @Test
    fun `keyword passes through and blank normalizes to null`() = runTest {
        val txRepo = FakeTransactionRepository()
        val vm = viewModel(txRepo)
        settle()

        vm.onKeywordChange("买书")
        settle()

        assertEquals("买书", txRepo.observeFilteredCalls.last().noteKeyword)

        vm.onKeywordChange("   ")
        settle()

        assertNull(txRepo.observeFilteredCalls.last().noteKeyword)
    }

    @Test
    fun `keyword is debounced before requery`() = runTest {
        val txRepo = FakeTransactionRepository()
        val vm = viewModel(txRepo)
        settle()
        val callsBefore = txRepo.observeFilteredCalls.size

        vm.onKeywordChange("买")
        runCurrent()
        assertEquals(callsBefore, txRepo.observeFilteredCalls.size)

        advanceTimeBy(299)
        runCurrent()
        assertEquals(callsBefore, txRepo.observeFilteredCalls.size)

        advanceTimeBy(1)
        runCurrent()
        assertEquals(callsBefore + 1, txRepo.observeFilteredCalls.size)
        assertEquals("买", txRepo.observeFilteredCalls.last().noteKeyword)
    }

    @Test
    fun `account filter defaults to all and loads accounts`() = runTest {
        val txRepo = FakeTransactionRepository()
        val accountRepo = FakeAccountRepository().apply {
            accountsFlow.value = listOf(Account(id = 7L, name = "微信"), Account(id = 8L, name = "支付宝"))
        }
        val vm = viewModel(txRepo, accountRepo = accountRepo)
        settle()

        assertEquals(AccountFilter.All, vm.uiState.value.accountFilter)
        assertEquals(
            listOf(Account(id = 7L, name = "微信"), Account(id = 8L, name = "支付宝")),
            vm.uiState.value.accounts,
        )
    }

    @Test
    fun `account list updates refresh state`() = runTest {
        val txRepo = FakeTransactionRepository()
        val accountRepo = FakeAccountRepository().apply {
            accountsFlow.value = listOf(Account(id = 7L, name = "微信"))
        }
        val vm = viewModel(txRepo, accountRepo = accountRepo)
        settle()

        assertEquals(listOf(Account(id = 7L, name = "微信")), vm.uiState.value.accounts)

        accountRepo.accountsFlow.value = listOf(
            Account(id = 7L, name = "微信"),
            Account(id = 8L, name = "支付宝"),
        )
        runCurrent()

        assertEquals(
            listOf(Account(id = 7L, name = "微信"), Account(id = 8L, name = "支付宝")),
            vm.uiState.value.accounts,
        )
    }

    @Test
    fun `selecting deleted account falls back to all and requeries`() = runTest {
        val txRepo = FakeTransactionRepository()
        val accountRepo = FakeAccountRepository().apply {
            accountsFlow.value = listOf(Account(id = 7L, name = "微信"), Account(id = 8L, name = "支付宝"))
        }
        val vm = viewModel(txRepo, accountRepo = accountRepo)
        settle()

        vm.selectAccountFilter(AccountFilter.Specific(7L))
        runCurrent()
        assertEquals(AccountFilter.Specific(7L), vm.uiState.value.accountFilter)

        val callsBefore = txRepo.observeFilteredCalls.size
        accountRepo.accountsFlow.value = listOf(Account(id = 8L, name = "支付宝"))
        runCurrent()

        assertEquals(AccountFilter.All, vm.uiState.value.accountFilter)
        assertEquals(AccountFilter.All, txRepo.observeFilteredCalls.last().accountFilter)
        assertEquals(callsBefore + 1, txRepo.observeFilteredCalls.size)
    }

    @Test
    fun `selecting specific account passes filter through and updates state`() = runTest {
        val txRepo = FakeTransactionRepository()
        val vm = viewModel(txRepo)
        settle()

        vm.selectAccountFilter(AccountFilter.Specific(7L))
        runCurrent()

        assertEquals(AccountFilter.Specific(7L), txRepo.observeFilteredCalls.last().accountFilter)
        assertEquals(AccountFilter.Specific(7L), vm.uiState.value.accountFilter)
    }

    @Test
    fun `selecting unspecified account passes filter through`() = runTest {
        val txRepo = FakeTransactionRepository()
        val vm = viewModel(txRepo)
        settle()

        vm.selectAccountFilter(AccountFilter.Unspecified)
        runCurrent()

        assertEquals(AccountFilter.Unspecified, txRepo.observeFilteredCalls.last().accountFilter)
        assertEquals(AccountFilter.Unspecified, vm.uiState.value.accountFilter)
    }

    @Test
    fun `account filter combines with time and keyword in single query`() = runTest {
        val txRepo = FakeTransactionRepository()
        val vm = viewModel(txRepo)
        settle()

        vm.selectTimeRange(TimeFilter.LAST_7_DAYS)
        vm.selectAccountFilter(AccountFilter.Specific(3L))
        vm.onKeywordChange("书")
        settle()

        val call = txRepo.observeFilteredCalls.last()
        assertEquals(TimeWindows.lastNDaysStartMillis(LocalDate.now(), 7), call.startInclusiveMs)
        assertEquals(AccountFilter.Specific(3L), call.accountFilter)
        assertEquals("书", call.noteKeyword)
    }

    @Test
    fun `switching account filter triggers requery`() = runTest {
        val txRepo = FakeTransactionRepository()
        val vm = viewModel(txRepo)
        settle()
        val callsBefore = txRepo.observeFilteredCalls.size

        vm.selectAccountFilter(AccountFilter.Unspecified)
        runCurrent()

        assertEquals(callsBefore + 1, txRepo.observeFilteredCalls.size)
        assertEquals(AccountFilter.Unspecified, txRepo.observeFilteredCalls.last().accountFilter)
    }

    @Test
    fun `confirmDelete success emits Deleted and clears pending`() = runTest {
        val txRepo = FakeTransactionRepository()
        val reportRepo = FakeReportRepository()
        val vm = viewModel(txRepo, reportRepo = reportRepo)
        settle()

        val events = mutableListOf<TransactionManageEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            vm.events.collect { events.add(it) }
        }

        val transaction = recent(id = 7L, occurredAt = 123_456L)
        vm.requestDelete(transaction)
        assertEquals(transaction, vm.uiState.value.pendingDelete)

        vm.confirmDelete()
        runCurrent()

        assertEquals(listOf(7L), txRepo.deletedIds)
        assertEquals(listOf(123_456L), reportRepo.invalidated)
        assertNull(vm.uiState.value.pendingDelete)
        assertFalse(vm.uiState.value.busy)
        assertEquals(listOf(TransactionManageEvent.Deleted), events)
    }

    @Test
    fun `confirmDelete failure emits Failed`() = runTest {
        val txRepo = FakeTransactionRepository().apply { deleteError = RuntimeException("db down") }
        val vm = viewModel(txRepo)
        settle()

        val events = mutableListOf<TransactionManageEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            vm.events.collect { events.add(it) }
        }

        vm.requestDelete(recent(id = 1L))
        vm.confirmDelete()
        runCurrent()

        assertEquals(listOf(TransactionManageEvent.Failed), events)
        assertNull(vm.uiState.value.pendingDelete)
        assertFalse(vm.uiState.value.busy)
    }

    @Test
    fun `confirmDelete ignores reentry while busy`() = runTest {
        val txRepo = FakeTransactionRepository().apply { deleteGate = CompletableDeferred<Unit>() }
        val vm = viewModel(txRepo)
        settle()

        vm.requestDelete(recent(id = 1L))
        vm.confirmDelete()
        vm.confirmDelete()

        assertEquals(listOf(1L), txRepo.deleteCalls)

        txRepo.deleteGate!!.complete(Unit)
        runCurrent()

        assertEquals(listOf(1L), txRepo.deletedIds)
        assertNull(vm.uiState.value.pendingDelete)
        assertFalse(vm.uiState.value.busy)
    }

    @Test
    fun `selectTab switches to transfers`() = runTest {
        val txRepo = FakeTransactionRepository()
        val vm = viewModel(txRepo)
        settle()

        vm.selectTab(ManageTab.TRANSFERS)

        assertEquals(ManageTab.TRANSFERS, vm.uiState.value.tab)
    }

    @Test
    fun `selectTab clears both pending deletes`() = runTest {
        val txRepo = FakeTransactionRepository()
        val vm = viewModel(txRepo)
        settle()

        vm.requestDelete(recent(id = 1L))
        vm.requestDeleteTransfer(
            TransferRow(
                id = 2L,
                fromAccountName = "微信",
                toAccountName = "支付宝",
                amountCents = 100L,
                note = null,
                occurredAt = 0L,
            ),
        )
        assertEquals(1L, vm.uiState.value.pendingDelete?.id)
        assertEquals(2L, vm.uiState.value.pendingTransferDelete?.id)

        vm.selectTab(ManageTab.TRANSFERS)

        assertNull(vm.uiState.value.pendingDelete)
        assertNull(vm.uiState.value.pendingTransferDelete)
        assertEquals(ManageTab.TRANSFERS, vm.uiState.value.tab)
    }

    @Test
    fun `observeTransfers maps account names into rows`() = runTest {
        val txRepo = FakeTransactionRepository()
        val transferRepo = FakeTransferRepository()
        val accountRepo = FakeAccountRepository().apply {
            accountsFlow.value = listOf(Account(id = 7L, name = "微信"), Account(id = 8L, name = "支付宝"))
        }
        transferRepo.transfersFlow.value = listOf(
            transfer(1L, from = 7L, to = 8L, amount = 5_000L, note = "调账", occurredAt = 123L),
        )
        val vm = viewModel(txRepo, accountRepo = accountRepo, transferRepo = transferRepo)
        settle()

        val row = vm.uiState.value.transfers.single()
        assertEquals(1L, row.id)
        assertEquals("微信", row.fromAccountName)
        assertEquals("支付宝", row.toAccountName)
        assertEquals(5_000L, row.amountCents)
        assertEquals("调账", row.note)
        assertEquals(123L, row.occurredAt)
    }

    @Test
    fun `transfer with deleted account falls back to deleted name`() = runTest {
        val txRepo = FakeTransactionRepository()
        val transferRepo = FakeTransferRepository()
        val accountRepo = FakeAccountRepository().apply {
            accountsFlow.value = listOf(Account(id = 7L, name = "微信"))
        }
        transferRepo.transfersFlow.value = listOf(transfer(1L, from = 7L, to = 99L))
        val vm = viewModel(txRepo, accountRepo = accountRepo, transferRepo = transferRepo)
        settle()

        val row = vm.uiState.value.transfers.single()
        assertEquals("微信", row.fromAccountName)
        assertEquals("已删除账户", row.toAccountName)
    }

    @Test
    fun `confirmDeleteTransfer success emits Deleted and clears pending`() = runTest {
        val txRepo = FakeTransactionRepository()
        val transferRepo = FakeTransferRepository()
        val vm = viewModel(txRepo, transferRepo = transferRepo)
        settle()

        val events = mutableListOf<TransactionManageEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            vm.events.collect { events.add(it) }
        }

        val row = TransferRow(
            id = 7L,
            fromAccountName = "微信",
            toAccountName = "支付宝",
            amountCents = 100L,
            note = null,
            occurredAt = 0L,
        )
        vm.requestDeleteTransfer(row)
        assertEquals(row, vm.uiState.value.pendingTransferDelete)

        vm.confirmDeleteTransfer()
        runCurrent()

        assertEquals(listOf(7L), transferRepo.deletedIds)
        assertNull(vm.uiState.value.pendingTransferDelete)
        assertFalse(vm.uiState.value.busy)
        assertEquals(listOf(TransactionManageEvent.Deleted), events)
    }

    @Test
    fun `confirmDeleteTransfer failure emits Failed`() = runTest {
        val txRepo = FakeTransactionRepository()
        val transferRepo = FakeTransferRepository().apply { deleteError = RuntimeException("db down") }
        val vm = viewModel(txRepo, transferRepo = transferRepo)
        settle()

        val events = mutableListOf<TransactionManageEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            vm.events.collect { events.add(it) }
        }

        vm.requestDeleteTransfer(
            TransferRow(
                id = 1L,
                fromAccountName = "微信",
                toAccountName = "支付宝",
                amountCents = 100L,
                note = null,
                occurredAt = 0L,
            ),
        )
        vm.confirmDeleteTransfer()
        runCurrent()

        assertEquals(listOf(TransactionManageEvent.Failed), events)
        assertNull(vm.uiState.value.pendingTransferDelete)
        assertFalse(vm.uiState.value.busy)
    }

    /** 推进虚拟时间跨过关键词防抖窗口，让初始查询与后续重查落定。 */
    private fun TestScope.settle() {
        advanceTimeBy(300)
        runCurrent()
    }

    private fun viewModel(
        txRepo: TransactionRepository,
        tagRepo: TagRepository = FakeTagRepository(),
        reportRepo: ReportRepository = FakeReportRepository(),
        accountRepo: AccountRepository = FakeAccountRepository(),
        transferRepo: TransferRepository = FakeTransferRepository(),
    ) = TransactionManageViewModel(
        transactionRepository = txRepo,
        accountRepository = accountRepo,
        tagRepository = tagRepo,
        deleteTransactionUseCase = DeleteTransactionUseCase(txRepo, reportRepo),
        transferRepository = transferRepo,
    )

    private fun recent(id: Long, occurredAt: Long = 0L) = RecentTransaction(
        id = id,
        amountCents = 1_000L,
        type = TransactionType.EXPENSE,
        tagName = "学习",
        occurredAt = occurredAt,
    )

    private fun transfer(
        id: Long,
        from: Long,
        to: Long,
        amount: Long = 1_000L,
        note: String? = null,
        occurredAt: Long = 0L,
    ) = Transfer(
        id = id,
        fromAccountId = from,
        toAccountId = to,
        amountCents = amount,
        note = note,
        occurredAt = occurredAt,
    )

    /** [TransactionRepository] 手写 fake：记录 observeFiltered 入参，驱动过滤流与删除路径。 */
    private class FakeTransactionRepository : TransactionRepository {

        val observeFilteredCalls = mutableListOf<FilterArgs>()
        val filteredFlow = MutableStateFlow<List<RecentTransaction>>(emptyList())
        var filteredError: Throwable? = null
        val deleteCalls = mutableListOf<Long>()
        val deletedIds = mutableListOf<Long>()
        var deleteError: Throwable? = null
        var deleteGate: CompletableDeferred<Unit>? = null

        override suspend fun add(transaction: Transaction): Long = 0L

        override suspend fun delete(transactionId: Long) {
            deleteError?.let { throw it }
            deleteCalls += transactionId
            deleteGate?.let { it.await() }
            deletedIds += transactionId
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
        ): Flow<List<RecentTransaction>> {
            observeFilteredCalls += FilterArgs(startInclusiveMs, endExclusiveMs, tagIds, noteKeyword, accountFilter)
            filteredError?.let { error -> return flow { throw error } }
            return filteredFlow
        }

        override fun observeUncategorizedCount(): Flow<Int> = flowOf(0)

        override suspend fun getUncategorized(): List<RecentTransaction> = emptyList()

        override suspend fun assignTags(assignments: Map<Long, List<Long>>) = Unit

        override suspend fun getOccurredAtsByTagIds(tagIds: List<Long>): List<Long> = emptyList()

        override suspend fun getById(id: Long): Transaction? = null

        override suspend fun updateTransaction(transaction: Transaction) = Unit
    }

    /** [AccountRepository] 手写 fake：账户观察流由 [accountsFlow] 驱动，可注入加载异常。 */
    private class FakeAccountRepository : AccountRepository {

        val accountsFlow = MutableStateFlow<List<Account>>(emptyList())
        var getAccountsError: Throwable? = null

        override fun observeAccounts(): Flow<List<Account>> = accountsFlow

        override suspend fun getAccounts(): List<Account> {
            getAccountsError?.let { throw it }
            return accountsFlow.value
        }

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

    /** [TagRepository] 手写 fake：返回预置根 / 子标签。 */
    private class FakeTagRepository : TagRepository {

        var rootTags: List<Tag> = emptyList()
        var childrenByParent: Map<Long, List<Tag>> = emptyMap()

        override fun observeChildren(parentId: Long?): Flow<List<Tag>> = flowOf(emptyList())

        override suspend fun getChildren(parentId: Long?): List<Tag> =
            if (parentId == null) rootTags else childrenByParent[parentId] ?: emptyList()

        override suspend fun getRecentUsedTags(sinceEpochMillis: Long, type: TransactionType, limit: Int): List<Tag> = emptyList()

        override suspend fun updateSortOrder(tags: List<Tag>) = Unit

        override suspend fun addSubTag(parentId: Long, name: String, icon: String?): Long = 0L

        override suspend fun addRootTag(name: String, icon: String?): Long = 0L

        override suspend fun renameTag(tagId: Long, newName: String) = Unit

        override suspend fun getDeleteImpact(tagId: Long): TagDeleteImpact = TagDeleteImpact(0, 0, emptyList())

        override suspend fun deleteTag(tagId: Long) = Unit

        override suspend fun mergeTags(keepTagId: Long, dropTagId: Long) = Unit
    }

    /** [ReportRepository] 手写 fake：记录标脏调用，供删除用例断言窗口覆盖。 */
    private class FakeReportRepository : ReportRepository {

        val invalidated = mutableListOf<Long>()

        override fun observeByType(type: ReportPeriodType): Flow<List<Report>> = flowOf(emptyList())

        override suspend fun getByKey(periodType: ReportPeriodType, periodKey: String): Report? = null

        override suspend fun upsert(report: Report) = Unit

        override suspend fun invalidateWhereWindowContains(epochMillis: Long) {
            invalidated += epochMillis
        }
    }

    /** [TransferRepository] 手写 fake：以 MutableStateFlow 驱动观察，记录删除调用并可注入异常。 */
    private class FakeTransferRepository : TransferRepository {

        val transfersFlow = MutableStateFlow<List<Transfer>>(emptyList())
        val deleteCalls = mutableListOf<Long>()
        val deletedIds = mutableListOf<Long>()
        var deleteError: Throwable? = null

        override fun observeTransfers(): Flow<List<Transfer>> = transfersFlow

        override suspend fun insertTransfer(transfer: Transfer): Long = 0L

        override suspend fun deleteById(id: Long) {
            deleteError?.let { throw it }
            deleteCalls += id
            deletedIds += id
        }
    }
}

/** 一次 observeFiltered 调用的入参快照。 */
private data class FilterArgs(
    val startInclusiveMs: Long?,
    val endExclusiveMs: Long?,
    val tagIds: List<Long>,
    val noteKeyword: String?,
    val accountFilter: AccountFilter,
)
