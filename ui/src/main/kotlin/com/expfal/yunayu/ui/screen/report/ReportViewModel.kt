package com.expfal.yunayu.ui.screen.report

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.expfal.yunayu.domain.report.GenerateReportUseCase
import com.expfal.yunayu.domain.report.model.Report
import com.expfal.yunayu.domain.report.model.ReportPeriodType
import com.expfal.yunayu.domain.repository.ReportRepository
import com.expfal.yunayu.domain.util.TimeWindow
import com.expfal.yunayu.domain.util.TimeWindows
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** 分析报告屏 UI 状态快照。 */
data class ReportUiState(
    val periodType: ReportPeriodType = ReportPeriodType.MONTHLY,
    val reports: List<Report> = emptyList(),
    val selectedPeriodKey: String? = null,
    val loading: Boolean = true,
    val generating: Boolean = false,
)

/**
 * 「分析报告」ViewModel：按周期类型观察报告列表、切换类型、失败报告重试。
 *
 * 列表经 [ReportRepository.observeByType] 观察，Room Flow 配 `.catch` 兜底避免流崩溃；切换类型
 * 重新订阅对应流。重试复用 [GenerateReportUseCase]（内部 upsert 覆盖），窗口参数由期键反推：
 * 月报取当期月窗口与上一月窗口，年报取当年窗口与上一年窗口；重试完成后由 Room Flow 自动刷新列表。
 */
@HiltViewModel
class ReportViewModel @Inject constructor(
    private val reportRepository: ReportRepository,
    private val generateReportUseCase: GenerateReportUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ReportUiState())
    val uiState: StateFlow<ReportUiState> = _uiState.asStateFlow()

    /** 当前报告列表观察协程，切换类型时取消重订阅。 */
    private var observeJob: Job? = null

    init {
        observeReports(ReportPeriodType.MONTHLY)
    }

    /** 切换周期类型并重新订阅对应列表；重复选择同一类型不做任何事。 */
    fun selectPeriodType(type: ReportPeriodType) {
        if (_uiState.value.periodType == type) return
        _uiState.update { it.copy(periodType = type, selectedPeriodKey = null) }
        observeReports(type)
    }

    /** 点选 / 取消点选某份报告，用于展开或收起详情。 */
    fun selectReport(periodKey: String) {
        _uiState.update {
            it.copy(selectedPeriodKey = if (it.selectedPeriodKey == periodKey) null else periodKey)
        }
    }

    /**
     * 重新生成指定报告；[Report.status] 为 FAILED 的条目才可重试（UI 已收敛，此处不重复校验）。
     *
     * 生成期间 [ReportUiState.generating] 置位，防重复重试；成功 / 失败后由 Room Flow 自动刷新
     * 列表并复位标记。[kotlinx.coroutines.CancellationException] 直接重抛，其余异常仅记日志降级。
     */
    fun retry(report: Report) {
        if (_uiState.value.generating) return
        val (current, previous) = runCatching { retryWindows(report) }
            .getOrElse { e ->
                Log.e(TAG, "Failed to derive retry windows for ${report.periodType}/${report.periodKey}", e)
                return
            }
        _uiState.update { it.copy(generating = true) }
        viewModelScope.launch {
            try {
                generateReportUseCase(
                    periodType = report.periodType,
                    periodKey = report.periodKey,
                    windowStartMs = current.startInclusiveMs,
                    windowEndMs = current.endExclusiveMs,
                    prevWindowStartMs = previous.startInclusiveMs,
                    prevWindowEndMs = previous.endExclusiveMs,
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Failed to regenerate report ${report.periodType}/${report.periodKey}", e)
            } finally {
                _uiState.update { it.copy(generating = false) }
            }
        }
    }

    /** 订阅指定类型的报告列表；观察失败降级为空列表并记日志。 */
    private fun observeReports(type: ReportPeriodType) {
        observeJob?.cancel()
        observeJob = viewModelScope.launch {
            reportRepository.observeByType(type)
                .catch { throwable ->
                    if (throwable is CancellationException) throw throwable
                    Log.e(TAG, "Failed to observe reports of type $type", throwable)
                    emit(emptyList())
                }
                .collect { reports ->
                    _uiState.update { state ->
                        state.copy(
                            reports = reports,
                            loading = false,
                            selectedPeriodKey = state.selectedPeriodKey
                                ?.takeIf { key -> reports.any { it.periodKey == key } },
                        )
                    }
                }
        }
    }

    /** 由报告期键反推「当期窗口、上期窗口」，作为重试的统计口径。 */
    private fun retryWindows(report: Report): Pair<TimeWindow, TimeWindow> = when (report.periodType) {
        ReportPeriodType.MONTHLY ->
            TimeWindows.monthWindowByKey(report.periodKey) to
                TimeWindows.previousMonthWindowByKey(report.periodKey)
        ReportPeriodType.ANNUAL ->
            TimeWindows.yearWindowByKey(report.periodKey) to
                TimeWindows.previousYearWindowByKey(report.periodKey)
    }

    private companion object {
        const val TAG = "ReportViewModel"
    }
}
