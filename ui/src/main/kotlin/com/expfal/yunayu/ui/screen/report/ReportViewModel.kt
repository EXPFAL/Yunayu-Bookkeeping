package com.expfal.yunayu.ui.screen.report

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.expfal.yunayu.domain.model.MonthlyBudgetSnapshot
import com.expfal.yunayu.domain.nl.NLTransactionParser
import com.expfal.yunayu.domain.report.DeepReadReportUseCase
import com.expfal.yunayu.domain.report.EnsureReportsUseCase
import com.expfal.yunayu.domain.report.GenerateReportUseCase
import com.expfal.yunayu.domain.report.LoadReportSeriesUseCase
import com.expfal.yunayu.domain.report.model.CategoryShare
import com.expfal.yunayu.domain.report.model.Report
import com.expfal.yunayu.domain.report.model.ReportPeriodType
import com.expfal.yunayu.domain.report.model.ReportSeriesSnapshot
import com.expfal.yunayu.domain.repository.MonthlyBudgetRepository
import com.expfal.yunayu.domain.repository.ReportRepository
import com.expfal.yunayu.domain.usecase.MonthlyBudgetEngine
import com.expfal.yunayu.domain.util.TimeWindow
import com.expfal.yunayu.domain.util.TimeWindows
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
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
    val periodType: ReportPeriodType = ReportPeriodType.WEEKLY,
    val reports: List<Report> = emptyList(),
    val selectedPeriodKey: String? = null,
    val loading: Boolean = true,
    val generating: Boolean = false,
    val categoryDetail: CategoryDetailUiState? = null,
    val series: ReportSeriesSnapshot? = null,
    val seriesLoading: Boolean = false,
    val deepReadAvailable: Boolean = false,
    val deepReading: Boolean = false,
    val deepReadMessage: String? = null,
    /** 今日预算额度（分）；0 表示未设置。 */
    val budgetCents: Long = 0L,
    /** 今日生活费快照（与首页同源）。 */
    val budgetSnapshot: MonthlyBudgetSnapshot? = null,
)

/**
 * 「分析报告」ViewModel：默认本周列表；点选进独立详情；观察今日预算快照；
 * 详情现算近 N 期序列，可选 AI 深读。
 */
@HiltViewModel
class ReportViewModel @Inject constructor(
    private val reportRepository: ReportRepository,
    private val generateReportUseCase: GenerateReportUseCase,
    private val ensureReportsUseCase: EnsureReportsUseCase,
    private val loadReportSeriesUseCase: LoadReportSeriesUseCase,
    private val deepReadReportUseCase: DeepReadReportUseCase,
    private val nlTransactionParser: NLTransactionParser,
    private val monthlyBudgetRepository: MonthlyBudgetRepository,
    private val monthlyBudgetEngine: MonthlyBudgetEngine,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ReportUiState())
    val uiState: StateFlow<ReportUiState> = _uiState.asStateFlow()

    private var observeJob: Job? = null
    private var seriesJob: Job? = null

    /** 通知深链：等本周列表到达后再 openDetail。 */
    private var pendingOpenDetailKey: String? = null

    init {
        observeReports(ReportPeriodType.WEEKLY)
        ensureCurrentPeriods()
        refreshDeepReadAvailability()
        observeBudget()
    }

    /** 切换周期类型并重新订阅对应列表；重复选择同一类型不做任何事。 */
    fun selectPeriodType(type: ReportPeriodType) {
        if (_uiState.value.periodType == type || type == ReportPeriodType.ANNUAL) return
        seriesJob?.cancel()
        _uiState.update {
            it.copy(
                periodType = type,
                selectedPeriodKey = null,
                categoryDetail = null,
                series = null,
                seriesLoading = false,
            )
        }
        observeReports(type)
        ensureCurrentPeriods()
    }

    /**
     * 打开详情：固定选中 [periodKey]（不再点按切换收起），并加载序列。
     * 由列表页导航到 [ReportDetailScreen] 前调用。
     */
    fun openDetail(periodKey: String) {
        val current = _uiState.value
        if (current.selectedPeriodKey == periodKey && current.series != null && !current.seriesLoading) {
            return
        }
        _uiState.update {
            it.copy(
                selectedPeriodKey = periodKey,
                categoryDetail = null,
                series = null,
                seriesLoading = true,
                deepReadMessage = null,
            )
        }
        seriesJob?.cancel()
        val report = current.reports.firstOrNull { it.periodKey == periodKey } ?: run {
            _uiState.update { it.copy(seriesLoading = false) }
            return
        }
        seriesJob = viewModelScope.launch {
            val snapshot = runCatching {
                loadReportSeriesUseCase(report.periodType, report.periodKey)
            }.getOrElse { e ->
                if (e is CancellationException) throw e
                Log.e(TAG, "Failed to load series for ${report.periodKey}", e)
                null
            }
            _uiState.update {
                if (it.selectedPeriodKey != periodKey) {
                    it
                } else {
                    it.copy(series = snapshot, seriesLoading = false)
                }
            }
        }
    }

    /** 通知入口：切到本周并打开今日对应期键详情（列表未就绪则挂起等待）。 */
    fun openThisWeekDetail() {
        val key = TimeWindows.weekPeriodKey(LocalDate.now())
        pendingOpenDetailKey = key
        if (_uiState.value.periodType != ReportPeriodType.WEEKLY) {
            selectPeriodType(ReportPeriodType.WEEKLY)
        } else if (_uiState.value.reports.any { it.periodKey == key }) {
            pendingOpenDetailKey = null
            openDetail(key)
        }
    }

    fun clearCategoryDetail() {
        _uiState.update { it.copy(categoryDetail = null) }
    }

    fun consumeDeepReadMessage() {
        _uiState.update { it.copy(deepReadMessage = null) }
    }

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
                if (_uiState.value.selectedPeriodKey == report.periodKey) {
                    val snapshot = runCatching {
                        loadReportSeriesUseCase(report.periodType, report.periodKey)
                    }.getOrNull()
                    _uiState.update { it.copy(series = snapshot, seriesLoading = false) }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Failed to regenerate report ${report.periodType}/${report.periodKey}", e)
            } finally {
                _uiState.update { it.copy(generating = false) }
            }
        }
    }

    fun deepRead(report: Report) {
        if (_uiState.value.deepReading || !_uiState.value.deepReadAvailable) return
        _uiState.update { it.copy(deepReading = true, deepReadMessage = null) }
        viewModelScope.launch {
            try {
                val text = deepReadReportUseCase(report, _uiState.value.series)
                _uiState.update {
                    it.copy(
                        deepReading = false,
                        deepReadMessage = if (text == null) "深度解读失败，请稍后重试" else null,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Deep read failed for ${report.periodKey}", e)
                _uiState.update {
                    it.copy(deepReading = false, deepReadMessage = "深度解读失败，请稍后重试")
                }
            }
        }
    }

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
                    val pending = pendingOpenDetailKey
                    if (pending != null && reports.any { it.periodKey == pending }) {
                        pendingOpenDetailKey = null
                        openDetail(pending)
                    }
                }
        }
    }

    private fun observeBudget() {
        viewModelScope.launch {
            val today = LocalDate.now()
            combine(
                monthlyBudgetRepository.observeMonthlyBudgetCents(),
                monthlyBudgetEngine.observeSnapshot(today),
            ) { budgetCents, snapshot ->
                budgetCents to snapshot
            }
                .catch { e ->
                    if (e is CancellationException) throw e
                    Log.e(TAG, "Failed to observe budget for reports", e)
                    emit(0L to MonthlyBudgetSnapshot(0, 0, 0, 1, 0, 0, 0))
                }
                .collect { (budgetCents, snapshot) ->
                    _uiState.update {
                        it.copy(
                            budgetCents = budgetCents,
                            budgetSnapshot = snapshot.takeIf { budgetCents > 0L },
                        )
                    }
                }
        }
    }

    private fun ensureCurrentPeriods() {
        viewModelScope.launch {
            runCatching { ensureReportsUseCase.ensure(LocalDate.now()) }
                .onFailure { e ->
                    if (e is CancellationException) throw e
                    Log.e(TAG, "Failed to ensure current reports", e)
                }
        }
    }

    private fun refreshDeepReadAvailability() {
        viewModelScope.launch {
            val available = runCatching { nlTransactionParser.isAvailable() }.getOrDefault(false)
            _uiState.update { it.copy(deepReadAvailable = available) }
        }
    }

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
