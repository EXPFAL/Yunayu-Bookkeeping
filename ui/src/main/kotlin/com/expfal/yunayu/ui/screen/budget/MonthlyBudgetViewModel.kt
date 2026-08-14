package com.expfal.yunayu.ui.screen.budget

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.expfal.yunayu.domain.model.MonthlyBudgetSnapshot
import com.expfal.yunayu.domain.repository.MonthlyBudgetRepository
import com.expfal.yunayu.domain.usecase.MonthlyBudgetEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

/** 月度预算看板 UI 状态快照。 */
data class MonthlyBudgetUiState(
    val loading: Boolean = true,
    val budgetCents: Long = 0L,
    val snapshot: MonthlyBudgetSnapshot? = null,
    val saving: Boolean = false,
)

/** 月度预算看板对外暴露的一次性事件。 */
sealed interface MonthlyBudgetEvent {

    /** 预算保存成功，提示 UI 关闭设置弹层并触发震动反馈。 */
    data object Saved : MonthlyBudgetEvent

    /** 预算保存失败，提示 UI 温和反馈「没保存上」。 */
    data object SaveFailed : MonthlyBudgetEvent
}

/**
 * 月度预算看板 ViewModel：观察预算额度与其月度快照，并承载预算额度的保存动作。
 *
 * 观察链以 [todayTicks] 为统一时间源：立即发射当前毫秒、随后 delay 到本地次日零点循环
 * 发射，同一 `now` 派生 LocalDate（systemDefault 口径）供快照计算复用，保证跨午夜自推进。
 * 预算额度流与快照流经 [combine] 同步组装；预算未设置时仓储发射 `0`，由 UI 转译为引导态。
 * 保存经 [events] 一次性发射，缓冲为 1 且满时丢弃最旧（沿用 Sprint 1 修复后的事件模式）。
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
open class MonthlyBudgetViewModel @Inject constructor(
    private val monthlyBudgetRepository: MonthlyBudgetRepository,
    private val monthlyBudgetEngine: MonthlyBudgetEngine,
) : ViewModel() {

    private val _uiState = MutableStateFlow(MonthlyBudgetUiState())
    val uiState: StateFlow<MonthlyBudgetUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<MonthlyBudgetEvent>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val events: Flow<MonthlyBudgetEvent> = _events.asSharedFlow()

    init {
        viewModelScope.launch {
            todayTicks()
                .flatMapLatest { now ->
                    val today = todayFromMillis(now)
                    combine(
                        monthlyBudgetRepository.observeMonthlyBudgetCents(),
                        monthlyBudgetEngine.observeSnapshot(today),
                    ) { budgetCents, snapshot ->
                        MonthlyBudgetUiState(loading = false, budgetCents = budgetCents, snapshot = snapshot)
                    }
                }
                .catch { throwable ->
                    Log.e(TAG, "Failed to observe monthly budget state", throwable)
                    _uiState.update { it.copy(loading = false) }
                }
                .collect { emitted -> _uiState.update { prev -> emitted.copy(saving = prev.saving) } }
        }
    }

    /**
     * 保存月度预算额度：saving 同步置位防重入；非正数非法输入直接发射 [MonthlyBudgetEvent.SaveFailed]
     * 且不落库；成功经 [MonthlyBudgetRepository.saveMonthlyBudgetCents] 后发射 [MonthlyBudgetEvent.Saved]。
     */
    fun saveMonthlyBudget(cents: Long) {
        if (_uiState.value.saving) return
        if (cents <= 0L) {
            Log.w(TAG, "Rejected non-positive monthly budget")
            _events.tryEmit(MonthlyBudgetEvent.SaveFailed)
            return
        }
        _uiState.update { it.copy(saving = true) }
        viewModelScope.launch {
            runCatching { monthlyBudgetRepository.saveMonthlyBudgetCents(cents) }
                .onSuccess {
                    _events.tryEmit(MonthlyBudgetEvent.Saved)
                    _uiState.update { it.copy(saving = false) }
                }
                .onFailure { throwable ->
                    if (throwable is CancellationException) throw throwable
                    Log.e(TAG, "Failed to save monthly budget", throwable)
                    _events.tryEmit(MonthlyBudgetEvent.SaveFailed)
                    _uiState.update { it.copy(saving = false) }
                }
        }
    }

    /** 以 systemDefault 口径从毫秒派生 LocalDate，供快照计算同源复用。 */
    private fun todayFromMillis(millis: Long): LocalDate =
        Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()

    /**
     * 立即发射当前毫秒，随后 delay 到本地次日零点循环发射，跨午夜自动推进 today。
     *
     * 声明为 `internal open` 以便单测用有限 ticker 子类覆盖，规避 `runTest` 的
     * `advanceUntilIdle` 对无限 delay 循环的挂起。
     */
    internal open fun todayTicks(): Flow<Long> = flow {
        while (true) {
            val now = System.currentTimeMillis()
            emit(now)
            delay(nextMidnightMillis(now) - now)
        }
    }

    private companion object {
        const val TAG = "MonthlyBudgetViewModel"
    }
}

/**
 * 从当前毫秒派生本地次日零点毫秒（systemDefault 口径），供 [MonthlyBudgetViewModel.todayTicks]
 * 计算下一次发射所需的 delay 时长；抽为纯函数以便 JVM 单测直接断言跨午夜推进边界。
 */
internal fun nextMidnightMillis(nowMillis: Long): Long =
    Instant.ofEpochMilli(nowMillis)
        .atZone(ZoneId.systemDefault())
        .toLocalDate()
        .plusDays(1)
        .atStartOfDay(ZoneId.systemDefault())
        .toInstant()
        .toEpochMilli()
