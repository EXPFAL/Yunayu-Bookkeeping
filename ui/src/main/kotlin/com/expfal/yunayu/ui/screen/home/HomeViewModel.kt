package com.expfal.yunayu.ui.screen.home

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.expfal.yunayu.domain.model.RecentTransaction
import com.expfal.yunayu.domain.repository.TransactionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** 首页最近记录列表 UI 状态快照。 */
data class HomeUiState(
    val loading: Boolean = true,
    val recent: List<RecentTransaction> = emptyList(),
    val heldCents: Long = 0L,
)

/**
 * 首页 ViewModel：观察最近 [RECENT_LIMIT] 笔交易摘要与持有资金（累计收入−累计支出）。
 *
 * 最近记录与持有资金各走独立观察链：任一链路失败仅影响自身字段，互不牵连——
 * 最近记录失败置 loading=false 保持空态；持有资金失败保持 0/上次值，不抹其他状态。
 */
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val transactionRepository: TransactionRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        observeRecent()
        observeHeldCents()
    }

    /** 观察最近 [RECENT_LIMIT] 笔交易摘要；失败仅置 loading=false，不影响持有资金字段。 */
    private fun observeRecent() {
        viewModelScope.launch {
            transactionRepository.observeRecent(RECENT_LIMIT)
                .catch { throwable ->
                    Log.e(TAG, "Failed to observe recent transactions", throwable)
                    _uiState.update { it.copy(loading = false) }
                }
                .collect { recent ->
                    _uiState.update { it.copy(loading = false, recent = recent) }
                }
        }
    }

    /** 观察持有资金；失败保持 0/上次值，只更新 heldCents 字段，不抹其他状态。 */
    private fun observeHeldCents() {
        viewModelScope.launch {
            transactionRepository.observeHeldCents()
                .catch { throwable ->
                    Log.e(TAG, "Failed to observe held cents", throwable)
                }
                .collect { heldCents ->
                    _uiState.update { it.copy(heldCents = heldCents) }
                }
        }
    }

    private companion object {
        const val TAG = "HomeViewModel"
        const val RECENT_LIMIT = 20
    }
}
