package com.expfal.yunayu.ui.screen.accountmanage

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.expfal.yunayu.domain.model.Account
import com.expfal.yunayu.domain.model.AccountDeleteImpact
import com.expfal.yunayu.domain.model.DuplicateAccountNameException
import com.expfal.yunayu.domain.repository.AccountRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** 账户列表行：账户 + 其下交易数（删除影响面提示用）。 */
data class AccountRow(
    val account: Account,
    val transactionCount: Int,
)

/** 删除目标快照：账户 + 将受影响的交易数。 */
data class AccountDeleteTarget(
    val account: Account,
    val affectedTransactionCount: Int,
)

/** 账户管理屏 UI 状态快照。 */
data class AccountManageUiState(
    val loading: Boolean = true,
    val accounts: List<AccountRow> = emptyList(),
    val busy: Boolean = false,
    val errorMessage: String? = null,
    val renamingAccount: Account? = null,
    val pendingDelete: AccountDeleteTarget? = null,
)

/** 账户管理屏对外暴露的一次性事件。 */
sealed interface AccountManageEvent {

    /** 账户新增成功，列表由观察链自动刷新。 */
    data object Added : AccountManageEvent

    /** 账户改名成功，列表由观察链自动刷新。 */
    data object Renamed : AccountManageEvent

    /** 账户删除成功，列表由观察链自动刷新。 */
    data object Deleted : AccountManageEvent

    /** 操作失败，携带面向用户的温和文案。 */
    data class Failed(val message: String) : AccountManageEvent
}

/**
 * 「管理账户」ViewModel：观察账户列表（含每账户交易数），承载增 / 改 / 删。
 *
 * 账户列表观察链以 [AccountRepository.observeAccounts] 为源，[flatMapLatest] 展开为逐账户
 * [AccountRepository.getDeleteImpact]（删除影响面即交易数，账户数少，取最小实现）的计数快照，
 * 异常由 `.catch` 兜底并置 loading=false。变更动作统一经 `busy` 防重入；新增 / 改名的重名
 * 与非法入参映射到 [AccountManageUiState.errorMessage] 内联展示（不崩溃），删除采用
 * 「先算影响面、再二次确认」两段式，成功 / 失败分别发出 [AccountManageEvent.Deleted] /
 * [AccountManageEvent.Failed]。
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class AccountManageViewModel @Inject constructor(
    private val accountRepository: AccountRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AccountManageUiState())
    val uiState: StateFlow<AccountManageUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<AccountManageEvent>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val events: Flow<AccountManageEvent> = _events.asSharedFlow()

    init {
        viewModelScope.launch {
            observeAccountsWithCounts()
                .catch { throwable ->
                    if (throwable is CancellationException) throw throwable
                    Log.e(TAG, "Failed to observe accounts", throwable)
                    _uiState.update { it.copy(loading = false) }
                }
                .collect { rows ->
                    _uiState.update { it.copy(loading = false, accounts = rows) }
                }
        }
    }

    /** 新增账户；重名 / 非法入参映射内联错误文案，成功发 [AccountManageEvent.Added]。 */
    fun addAccount(name: String) {
        if (_uiState.value.busy) return
        _uiState.update { it.copy(busy = true, errorMessage = null) }
        viewModelScope.launch {
            runCatching { accountRepository.addAccount(name) }
                .onSuccess {
                    _events.tryEmit(AccountManageEvent.Added)
                    _uiState.update { it.copy(busy = false, errorMessage = null) }
                }
                .onFailure { throwable -> handleActionFailure(throwable, "Failed to add account") }
        }
    }

    /** 进入改名态：记录待改名账户，供 UI 弹出改名弹窗。 */
    fun requestRename(account: Account) {
        if (_uiState.value.busy) return
        _uiState.update { it.copy(renamingAccount = account, errorMessage = null) }
    }

    /** 取消改名态，清空改名相关错误反馈。 */
    fun dismissRename() {
        _uiState.update { it.copy(renamingAccount = null, errorMessage = null) }
    }

    /** 提交改名；成功发 [AccountManageEvent.Renamed] 并关闭改名态，失败保留改名态并内联错误。 */
    fun rename(accountId: Long, newName: String) {
        if (_uiState.value.busy) return
        _uiState.update { it.copy(busy = true, errorMessage = null) }
        viewModelScope.launch {
            runCatching { accountRepository.renameAccount(accountId, newName) }
                .onSuccess {
                    _events.tryEmit(AccountManageEvent.Renamed)
                    _uiState.update { it.copy(busy = false, renamingAccount = null, errorMessage = null) }
                }
                .onFailure { throwable -> handleActionFailure(throwable, "Failed to rename account") }
        }
    }

    /** 计算删除影响面并置入 [AccountManageUiState.pendingDelete]，供 UI 二次确认。 */
    fun requestDelete(account: Account) {
        if (_uiState.value.busy) return
        _uiState.update { it.copy(busy = true, errorMessage = null) }
        viewModelScope.launch {
            runCatching { accountRepository.getDeleteImpact(account.id) }
                .onSuccess { impact ->
                    _uiState.update {
                        it.copy(
                            busy = false,
                            pendingDelete = AccountDeleteTarget(account, impact.affectedTransactionCount),
                        )
                    }
                }
                .onFailure { throwable -> handleActionFailure(throwable, "Failed to compute delete impact") }
        }
    }

    /** 确认删除：执行后清空 pendingDelete 并发 [AccountManageEvent.Deleted]；失败发 [AccountManageEvent.Failed]。 */
    fun confirmDelete() {
        val pending = _uiState.value.pendingDelete ?: return
        if (_uiState.value.busy) return
        _uiState.update { it.copy(busy = true) }
        viewModelScope.launch {
            runCatching { accountRepository.deleteAccount(pending.account.id) }
                .onSuccess {
                    _events.tryEmit(AccountManageEvent.Deleted)
                    _uiState.update { it.copy(busy = false, pendingDelete = null, errorMessage = null) }
                }
                .onFailure { throwable ->
                    if (throwable is CancellationException) throw throwable
                    Log.e(TAG, "Failed to delete account", throwable)
                    _events.tryEmit(AccountManageEvent.Failed("删除失败，请重试"))
                    _uiState.update { it.copy(busy = false, pendingDelete = null) }
                }
        }
    }

    /** 取消删除确认，清空 [AccountManageUiState.pendingDelete]。 */
    fun cancelDelete() {
        _uiState.update { it.copy(pendingDelete = null) }
    }

    /** 清空错误反馈（关闭弹窗时调用）。 */
    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    /**
     * 组装账户观察链：账户列表 → 逐账户取删除影响面（交易数）。
     *
     * 最小实现说明：账户数通常很少，直接逐账户调 [AccountRepository.getDeleteImpact]
     * （内部为单条 COUNT 查询）即可得到「名称 + 交易数」行；计数随账户列表重发射刷新。
     */
    private fun observeAccountsWithCounts(): Flow<List<AccountRow>> =
        accountRepository.observeAccounts().flatMapLatest { accounts ->
            if (accounts.isEmpty()) {
                flowOf(emptyList())
            } else {
                flow {
                    val rows = accounts.map { account ->
                        val count = runCatching { accountRepository.getDeleteImpact(account.id) }
                            .onFailure { throwable ->
                                if (throwable is CancellationException) throw throwable
                                Log.w(TAG, "Failed to load transaction count for account ${account.id}", throwable)
                            }
                            .getOrDefault(AccountDeleteImpact(0))
                            .affectedTransactionCount
                        AccountRow(account = account, transactionCount = count)
                    }
                    emit(rows)
                }
            }
        }

    /** 变更动作统一失败处理：CancellationException 重抛，其余映射错误文案并复位 busy。 */
    private fun handleActionFailure(throwable: Throwable, logMessage: String) {
        if (throwable is CancellationException) throw throwable
        Log.e(TAG, logMessage, throwable)
        _uiState.update { it.copy(busy = false, errorMessage = accountErrorMessage(throwable)) }
    }

    /** 将仓储异常映射为面向用户的温和文案。 */
    private fun accountErrorMessage(throwable: Throwable): String = when (throwable) {
        is DuplicateAccountNameException -> "同名账户已存在"
        is IllegalArgumentException -> throwable.message ?: "操作不合法"
        else -> "操作失败，请重试"
    }

    private companion object {
        const val TAG = "AccountManageViewModel"
    }
}
