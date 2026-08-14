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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

/** 首页最近记录列表 UI 状态快照。 */
data class HomeUiState(
    val loading: Boolean = true,
    val recent: List<RecentTransaction> = emptyList(),
)

/**
 * 首页 ViewModel：观察最近 [RECENT_LIMIT] 笔交易摘要。
 *
 * [TransactionRepository.observeRecent] 已做 `distinctUntilChanged`，新交易写入即重新发射；
 * 观察链 .catch 兜底，失败时置 loading=false 保持空态，避免首页因数据源异常崩溃。
 */
@HiltViewModel
class HomeViewModel @Inject constructor(
    transactionRepository: TransactionRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            transactionRepository.observeRecent(RECENT_LIMIT)
                .map { recent -> HomeUiState(loading = false, recent = recent) }
                .catch { throwable ->
                    Log.e(TAG, "Failed to observe recent transactions", throwable)
                    _uiState.value = HomeUiState(loading = false)
                }
                .collect { _uiState.value = it }
        }
    }

    private companion object {
        const val TAG = "HomeViewModel"
        const val RECENT_LIMIT = 20
    }
}
