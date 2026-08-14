package com.expfal.yunayu.ui.screen.budget

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.expfal.yunayu.domain.model.BudgetSnapshot
import com.expfal.yunayu.domain.model.Semester
import com.expfal.yunayu.domain.repository.SemesterRepository
import com.expfal.yunayu.domain.usecase.SemesterBudgetEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

/** 预算看板 UI 状态快照。 */
data class BudgetUiState(
    val loading: Boolean = true,
    val semester: Semester? = null,
    val snapshot: BudgetSnapshot? = null,
)

/** 预算看板对外暴露的一次性事件。 */
sealed interface BudgetEvent {

    /** 学期保存成功，提示 UI 关闭设置弹层并触发震动反馈。 */
    data object Saved : BudgetEvent

    /** 学期保存失败，提示 UI 温和反馈「没保存上」。 */
    data object SaveFailed : BudgetEvent
}

/**
 * 预算看板 ViewModel：观察当前激活学期与其预算快照，并承载学期设置的保存动作。
 *
 * 学期不存在时发射「无学期」引导态；存在时经 [SemesterBudgetEngine.observeBudgetSnapshot]
 * 组合快照，同时保留 [Semester] 引用供编辑面板预填。保存事件经 [events] 一次性发射，
 * 缓冲为 1 且满时丢弃最旧（沿用 Sprint 1 修复后的事件模式，杜绝弹层关闭后回放）。
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class BudgetViewModel @Inject constructor(
    private val engine: SemesterBudgetEngine,
    private val semesterRepository: SemesterRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(BudgetUiState())
    val uiState: StateFlow<BudgetUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<BudgetEvent>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val events: Flow<BudgetEvent> = _events.asSharedFlow()

    init {
        viewModelScope.launch {
            semesterRepository.observeActiveSemester(System.currentTimeMillis())
                .flatMapLatest { semester ->
                    if (semester == null) {
                        flowOf(BudgetUiState(loading = false))
                    } else {
                        engine.observeBudgetSnapshot(semester.id, LocalDate.now())
                            .map { snapshot ->
                                BudgetUiState(loading = false, semester = semester, snapshot = snapshot)
                            }
                    }
                }
                .collect { _uiState.value = it }
        }
    }

    /**
     * 保存学期：编辑既有学期时保留其 id 与考试周/假期区间；新建时 id=0、区间为空列表。
     *
     * 输入校验在调用方 Composable 与 ViewModel 双重兜底：名称非空白、起始日期不晚于
     * 结束日期、预算金额大于 0。非法输入直接忽略，不落库。
     */
    fun saveSemester(
        name: String,
        startDate: LocalDate,
        endDate: LocalDate,
        totalBudgetCents: Long,
    ) {
        if (!isValid(name, startDate, endDate, totalBudgetCents)) return

        val current = _uiState.value.semester
        val semester = if (current != null) {
            current.copy(
                name = name,
                startDate = startDate,
                endDate = endDate,
                totalBudgetCents = totalBudgetCents,
            )
        } else {
            Semester(
                id = 0L,
                name = name,
                startDate = startDate,
                endDate = endDate,
                totalBudgetCents = totalBudgetCents,
                examWeekRanges = emptyList(),
                vacationRanges = emptyList(),
            )
        }

        viewModelScope.launch {
            runCatching { semesterRepository.save(semester) }
                .onSuccess { _events.tryEmit(BudgetEvent.Saved) }
                .onFailure { throwable ->
                    Log.e(TAG, "Failed to save semester", throwable)
                    _events.tryEmit(BudgetEvent.SaveFailed)
                }
        }
    }

    private fun isValid(
        name: String,
        startDate: LocalDate,
        endDate: LocalDate,
        totalBudgetCents: Long,
    ): Boolean = name.isNotBlank() && !startDate.isAfter(endDate) && totalBudgetCents > 0L

    private companion object {
        const val TAG = "BudgetViewModel"
    }
}
