package com.expfal.yunayu.ui.screen.transactionmanage

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.expfal.yunayu.domain.model.Account
import com.expfal.yunayu.domain.model.AccountFilter
import com.expfal.yunayu.domain.model.RecentTransaction
import com.expfal.yunayu.domain.model.Tag
import com.expfal.yunayu.domain.repository.AccountRepository
import com.expfal.yunayu.domain.repository.TagRepository
import com.expfal.yunayu.domain.repository.TransactionRepository
import com.expfal.yunayu.domain.usecase.DeleteTransactionUseCase
import com.expfal.yunayu.domain.util.TimeWindows
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

/** 收支管理时间筛选维度。 */
enum class TimeFilter {
    ALL,
    LAST_7_DAYS,
    LAST_30_DAYS,
    THIS_MONTH,
}

/** 收支管理屏 UI 状态快照。 */
data class TransactionManageUiState(
    val transactions: List<RecentTransaction> = emptyList(),
    val loading: Boolean = true,
    val timeRange: TimeFilter = TimeFilter.ALL,
    val selectedTagIds: Set<Long> = emptySet(),
    val keyword: String = "",
    val accounts: List<Account> = emptyList(),
    val accountFilter: AccountFilter = AccountFilter.All,
    val allTagsByRoot: Map<Tag, List<Tag>> = emptyMap(),
    val pendingDelete: RecentTransaction? = null,
    val busy: Boolean = false,
    val uncategorizedCount: Int = 0,
)

/** 收支管理屏对外暴露的一次性事件。 */
sealed interface TransactionManageEvent {

    /** 交易删除成功，列表由观察链自动刷新。 */
    data object Deleted : TransactionManageEvent

    /** 交易删除失败，提示 UI 温和反馈。 */
    data object Failed : TransactionManageEvent
}

/** 观察链组装结果：时间窗 + 标签集合 + 备注关键词 + 账户筛选。 */
private data class FilterParams(
    val timeRange: TimeFilter,
    val tagIds: Set<Long>,
    val keyword: String,
    val accountFilter: AccountFilter,
)

/**
 * 「收支管理」ViewModel：按时间窗、标签、备注关键词组合筛选交易，并承载交易删除。
 *
 * 筛选链路以 [combine] 组装三个 [MutableStateFlow]（时间窗 / 标签集合 / 关键词），关键词经
 * [debounce] 防抖后经 [flatMapLatest] 订阅 [TransactionRepository.observeFiltered]，任一环节异常
 * 由 `.catch` 兜底并置 loading=false。删除采用「先请求、再确认」两段式，`busy` 防重入，
 * 成功 / 失败分别发出 [TransactionManageEvent.Deleted] / [TransactionManageEvent.Failed]。
 */
@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
@HiltViewModel
class TransactionManageViewModel @Inject constructor(
    private val transactionRepository: TransactionRepository,
    private val accountRepository: AccountRepository,
    private val tagRepository: TagRepository,
    private val deleteTransactionUseCase: DeleteTransactionUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(TransactionManageUiState())
    val uiState: StateFlow<TransactionManageUiState> = _uiState.asStateFlow()

    private val timeRangeFlow = MutableStateFlow(TimeFilter.ALL)
    private val selectedTagIdsFlow = MutableStateFlow<Set<Long>>(emptySet())
    private val keywordFlow = MutableStateFlow("")
    private val accountFilterFlow = MutableStateFlow<AccountFilter>(AccountFilter.All)

    private val _events = MutableSharedFlow<TransactionManageEvent>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val events: Flow<TransactionManageEvent> = _events.asSharedFlow()

    init {
        observeTransactions()
        loadAllTags()
        observeUncategorizedCount()
        observeAccounts()
    }

    /** 切换时间筛选维度，立即重查。 */
    fun selectTimeRange(filter: TimeFilter) {
        timeRangeFlow.value = filter
        _uiState.update { it.copy(timeRange = filter) }
    }

    /** 切换标签选中态：已选则移除、未选则加入（多选）。 */
    fun toggleTagSelection(tagId: Long) {
        val next = selectedTagIdsFlow.value.let { if (tagId in it) it - tagId else it + tagId }
        selectedTagIdsFlow.value = next
        _uiState.update { it.copy(selectedTagIds = next) }
    }

    /** 清空标签筛选。 */
    fun clearTagSelection() {
        selectedTagIdsFlow.value = emptySet()
        _uiState.update { it.copy(selectedTagIds = emptySet()) }
    }

    /** 更新备注关键词（经防抖后触发重查）。 */
    fun onKeywordChange(keyword: String) {
        keywordFlow.value = keyword
        _uiState.update { it.copy(keyword = keyword) }
    }

    /** 切换账户筛选维度，立即重查。 */
    fun selectAccountFilter(filter: AccountFilter) {
        accountFilterFlow.value = filter
        _uiState.update { it.copy(accountFilter = filter) }
    }

    /** 请求删除一笔交易，置入 [TransactionManageUiState.pendingDelete] 供 UI 二次确认。 */
    fun requestDelete(transaction: RecentTransaction) {
        _uiState.update { it.copy(pendingDelete = transaction) }
    }

    /** 确认删除：成功后清空 pendingDelete 并发 [TransactionManageEvent.Deleted]，失败发 [TransactionManageEvent.Failed]。 */
    fun confirmDelete() {
        val pending = _uiState.value.pendingDelete ?: return
        if (_uiState.value.busy) return
        _uiState.update { it.copy(busy = true) }
        viewModelScope.launch {
            runCatching { deleteTransactionUseCase(pending.id, pending.occurredAt) }
                .onSuccess {
                    _events.tryEmit(TransactionManageEvent.Deleted)
                    _uiState.update { it.copy(busy = false, pendingDelete = null) }
                }
                .onFailure { throwable ->
                    if (throwable is CancellationException) throw throwable
                    Log.e(TAG, "Failed to delete transaction", throwable)
                    _events.tryEmit(TransactionManageEvent.Failed)
                    _uiState.update { it.copy(busy = false, pendingDelete = null) }
                }
        }
    }

    /** 取消删除确认，清空 [TransactionManageUiState.pendingDelete]。 */
    fun cancelDelete() {
        _uiState.update { it.copy(pendingDelete = null) }
    }

    /** 观察未分类交易数供「整理」入口展示；失败降级为 0，取消异常直接重抛。 */
    private fun observeUncategorizedCount() {
        viewModelScope.launch {
            transactionRepository.observeUncategorizedCount()
                .catch { throwable ->
                    if (throwable is CancellationException) throw throwable
                    Log.e(TAG, "Failed to observe uncategorized count", throwable)
                    _uiState.update { it.copy(uncategorizedCount = 0) }
                }
                .collect { count ->
                    _uiState.update { it.copy(uncategorizedCount = count) }
                }
        }
    }

    /** 组装筛选观察链：组合时间窗 / 标签 / 关键词 / 账户筛选（空关键词直通、非空防抖），订阅仓储过滤流。 */
    private fun observeTransactions() {
        viewModelScope.launch {
            combine(
                timeRangeFlow,
                selectedTagIdsFlow,
                keywordFlow.debounce { keyword ->
                    if (keyword.isEmpty()) 0L else KEYWORD_DEBOUNCE_MILLIS
                },
                accountFilterFlow,
            ) { range, tagIds, keyword, accountFilter ->
                FilterParams(range, tagIds, keyword, accountFilter)
            }
                .flatMapLatest { params ->
                    val (start, end) = windowFor(params.timeRange)
                    transactionRepository.observeFiltered(
                        startInclusiveMs = start,
                        endExclusiveMs = end,
                        tagIds = params.tagIds.toList(),
                        noteKeyword = params.keyword.ifBlank { null },
                        accountFilter = params.accountFilter,
                    )
                }
                .catch { throwable ->
                    if (throwable is CancellationException) throw throwable
                    Log.e(TAG, "Failed to observe filtered transactions", throwable)
                    _uiState.update { it.copy(loading = false) }
                }
                .collect { transactions ->
                    _uiState.update { it.copy(loading = false, transactions = transactions) }
                }
        }
    }

    /**
     * 观察账户列表供账户筛选 chips 展示，账户增删改后实时刷新。
     *
     * 失效筛选自动回退：若当前筛选为 [AccountFilter.Specific] 且该账户已从列表消失（如被删除），
     * 则将 [accountFilterFlow] 与 [TransactionManageUiState.accountFilter] 一并回退为
     * [AccountFilter.All]，经 [combine] 触发重查，避免列表恒空死状态。
     */
    private fun observeAccounts() {
        viewModelScope.launch {
            accountRepository.observeAccounts()
                .catch { throwable ->
                    if (throwable is CancellationException) throw throwable
                    Log.w(TAG, "Failed to observe accounts", throwable)
                }
                .collect { accounts ->
                    _uiState.update { it.copy(accounts = accounts) }
                    val current = accountFilterFlow.value
                    if (current is AccountFilter.Specific && accounts.none { it.id == current.accountId }) {
                        accountFilterFlow.value = AccountFilter.All
                        _uiState.update { it.copy(accountFilter = AccountFilter.All) }
                    }
                }
        }
    }

    /** 加载全部标签并按根分组，供标签筛选弹层展示；失败降级为空映射。 */
    private fun loadAllTags() {
        viewModelScope.launch {
            val mapping = runCatching { loadAllTagsByRoot() }
                .onFailure { throwable ->
                    if (throwable is CancellationException) throw throwable
                    Log.w(TAG, "Failed to load all tags", throwable)
                }
                .getOrDefault(emptyMap())
            _uiState.update { it.copy(allTagsByRoot = mapping) }
        }
    }

    /** 根列表经 `getChildren(null)` 获取，再逐根拉子标签；任一失败降级为空列表。 */
    private suspend fun loadAllTagsByRoot(): Map<Tag, List<Tag>> {
        val roots = tagRepository.getChildren(parentId = null)
        return roots.associateWith { root ->
            runCatching { tagRepository.getChildren(parentId = root.id) }
                .onFailure { throwable ->
                    if (throwable is CancellationException) throw throwable
                    Log.w(TAG, "Failed to load children for root ${root.id}", throwable)
                }
                .getOrDefault(emptyList())
        }
    }

    /** 时间窗映射：ALL 不设边界，其余以当前日期计算窗口起点。 */
    private fun windowFor(filter: TimeFilter): Pair<Long?, Long?> {
        val today = LocalDate.now()
        return when (filter) {
            TimeFilter.ALL -> null to null
            TimeFilter.LAST_7_DAYS -> TimeWindows.lastNDaysStartMillis(today, 7) to null
            TimeFilter.LAST_30_DAYS -> TimeWindows.lastNDaysStartMillis(today, 30) to null
            TimeFilter.THIS_MONTH -> TimeWindows.monthStartMillis(today) to null
        }
    }

    private companion object {
        const val TAG = "TransactionManageViewModel"
        const val KEYWORD_DEBOUNCE_MILLIS = 300L
    }
}
