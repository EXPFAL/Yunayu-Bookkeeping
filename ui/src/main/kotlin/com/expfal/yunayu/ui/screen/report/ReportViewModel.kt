package com.expfal.yunayu.ui.screen.report

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.expfal.yunayu.domain.report.EnsureReportsUseCase
import com.expfal.yunayu.domain.report.GenerateReportUseCase
import com.expfal.yunayu.domain.report.model.CategoryShare
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
import java.time.LocalDate
import javax.inject.Inject

/** 选中分类的轻量快照：金额、占比，以及下钻用标签。 */
data class CategoryDetailUiState(
    val label: String = "",
    val expenseCents: Long = 0L,
    val percent: Int = 0,
    val isOtherBucket: Boolean = false,
    /** 下钻用 tagId；「其他」桶为 null 表示时间窗内不按标签过滤。 */
    val drillTagId: Long? = null,
)

/** 分析报告屏 UI 状态快照。 */
data class ReportUiState(
    val periodType: ReportPeriodType = ReportPeriodType.MONTHLY,
    val reports: List<Report> = emptyList(),
    val selectedPeriodKey: String? = null,
    val loading: Boolean = true,
    val generating: Boolean = false,
    val categoryDetail: CategoryDetailUiState? = null,
)

/**
 * 「分析报告」ViewModel：按周期类型观察报告列表、切换类型、失败报告重试，
 * 并记录饼图选中分类（金额 / 占比 / 下钻标签）。
 */
@HiltViewModel
class ReportViewModel @Inject constructor(
    private val reportRepository: ReportRepository,
    private val generateReportUseCase: GenerateReportUseCase,
    private val ensureReportsUseCase: EnsureReportsUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ReportUiState())
    val uiState: StateFlow<ReportUiState> = _uiState.asStateFlow()

    /** 当前报告列表观察协程，切换类型时取消重订阅。 */
    private var observeJob: Job? = null

    init {
        observeReports(ReportPeriodType.MONTHLY)
        ensureCurrentPeriods()
    }

    /** 切换周期类型并重新订阅对应列表；重复选择同一类型不做任何事。 */
    fun selectPeriodType(type: ReportPeriodType) {
        if (_uiState.value.periodType == type || type == ReportPeriodType.ANNUAL) return
        _uiState.update {
            it.copy(periodType = type, selectedPeriodKey = null, categoryDetail = null)
        }
        observeReports(type)
        ensureCurrentPeriods()
    }

    /** 点选 / 取消点选某份报告，用于展开或收起详情。 */
    fun selectReport(periodKey: String) {
        _uiState.update {
            val nextKey = if (it.selectedPeriodKey == periodKey) null else periodKey
            it.copy(selectedPeriodKey = nextKey, categoryDetail = null)
        }
    }

    /** 清空分类选中详情。 */
    fun clearCategoryDetail() {
        _uiState.update { it.copy(categoryDetail = null) }
    }

    /**
     * 记录选中分类。[isOtherBucket] 表示饼图合成的「其他」残差桶。
     * 金额与占比直接取自报告快照，不再另查流水。
     */
    fun selectCategoryShare(share: CategoryShare, isOtherBucket: Boolean) {
        val label = if (isOtherBucket) "其他" else share.tagName ?: "未分类"
        _uiState.update {
            it.copy(
                categoryDetail = CategoryDetailUiState(
                    label = label,
                    expenseCents = share.cents,
                    percent = share.percent,
                    isOtherBucket = isOtherBucket,
                    drillTagId = if (isOtherBucket) null else share.tagId,
                ),
            )
        }
    }

    /**
     * 重新生成指定报告；[Report.status] 为 FAILED / STALE 的条目才可重试（UI 已收敛，此处不重复校验）。
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

    /** 补齐本周 / 本月等缺失报告（失败仅记日志）。 */
    private fun ensureCurrentPeriods() {
        viewModelScope.launch {
            runCatching { ensureReportsUseCase.ensure(LocalDate.now()) }
                .onFailure { e ->
                    if (e is CancellationException) throw e
                    Log.e(TAG, "Failed to ensure current reports", e)
                }
        }
    }

    /** 由报告期键反推「当期窗口、上期窗口」，作为重试的统计口径。 */
    private fun retryWindows(report: Report): Pair<TimeWindow, TimeWindow> = when (report.periodType) {
        ReportPeriodType.WEEKLY ->
            TimeWindows.weekWindowByKey(report.periodKey) to
                TimeWindows.previousWeekWindowByKey(report.periodKey)
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
