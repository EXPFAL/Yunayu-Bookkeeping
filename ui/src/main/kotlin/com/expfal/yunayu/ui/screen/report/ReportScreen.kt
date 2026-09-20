@file:OptIn(ExperimentalMaterial3Api::class)

package com.expfal.yunayu.ui.screen.report

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.expfal.yunayu.domain.model.RecentTransaction
import com.expfal.yunayu.domain.report.model.CategoryShare
import com.expfal.yunayu.domain.report.model.LocalInsight
import com.expfal.yunayu.domain.report.model.Report
import com.expfal.yunayu.domain.report.model.ReportPeriodType
import com.expfal.yunayu.domain.report.model.ReportStatus
import com.expfal.yunayu.ui.component.PIE_COLORS
import com.expfal.yunayu.ui.component.PieChart
import com.expfal.yunayu.ui.util.formatCents
import com.expfal.yunayu.ui.util.formatTime
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.abs

/**
 * 「分析报告」全屏：顶部周/月/年切换，中部按期键倒序的报告列表，点选展开详情；失败条目可重试。
 *
 * [onDrillToTransactions]：分类下钻到收支管理（时间窗 + 可选标签）。
 */
@Composable
fun ReportScreen(
    onBack: () -> Unit,
    onDrillToTransactions: (startMs: Long, endMs: Long, tagId: Long?) -> Unit = { _, _, _ -> },
    viewModel: ReportViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val budgetCents by viewModel.budgetCents.collectAsStateWithLifecycle()
    BackHandler(onBack = onBack)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("分析报告") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 24.dp),
        ) {
            PeriodTypeToggle(uiState.periodType, viewModel::selectPeriodType)
            Spacer(modifier = Modifier.height(16.dp))
            when {
                uiState.loading -> LoadingState()
                uiState.reports.isEmpty() -> EmptyState()
                else -> ReportList(
                    reports = uiState.reports,
                    selectedPeriodKey = uiState.selectedPeriodKey,
                    generating = uiState.generating,
                    budgetCents = budgetCents,
                    categoryDetail = uiState.categoryDetail,
                    onSelect = viewModel::selectReport,
                    onRetry = viewModel::retry,
                    onSelectCategory = viewModel::selectCategoryShare,
                    onClearCategory = viewModel::clearCategoryDetail,
                    onDrillToTransactions = { report, tagId ->
                        onDrillToTransactions(report.windowStartMs, report.windowEndMs, tagId)
                    },
                )
            }
        }
    }
}

/** 周度/月度/年度切换控件，样式对齐快捷记账的收/支 [FilterChip]。 */
@Composable
private fun PeriodTypeToggle(selected: ReportPeriodType, onSelect: (ReportPeriodType) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(
            selected = selected == ReportPeriodType.WEEKLY,
            onClick = { onSelect(ReportPeriodType.WEEKLY) },
            label = { Text("本周") },
        )
        FilterChip(
            selected = selected == ReportPeriodType.MONTHLY,
            onClick = { onSelect(ReportPeriodType.MONTHLY) },
            label = { Text("月度") },
        )
        FilterChip(
            selected = selected == ReportPeriodType.ANNUAL,
            onClick = { onSelect(ReportPeriodType.ANNUAL) },
            label = { Text("年度") },
        )
    }
}

/** 报告列表：按期键倒序渲染每行，选中报告的详情作为尾随 item 展开。 */
@Composable
private fun ReportList(
    reports: List<Report>,
    selectedPeriodKey: String?,
    generating: Boolean,
    budgetCents: Long,
    categoryDetail: CategoryDetailUiState?,
    onSelect: (String) -> Unit,
    onRetry: (Report) -> Unit,
    onSelectCategory: (Report, CategoryShare, Boolean, Int) -> Unit,
    onClearCategory: () -> Unit,
    onDrillToTransactions: (Report, Long?) -> Unit,
) {
    val selected = reports.firstOrNull { it.periodKey == selectedPeriodKey }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(reports, key = { it.periodKey }) { report ->
            ReportRow(
                report = report,
                selected = report.periodKey == selectedPeriodKey,
                generating = generating,
                onClick = { onSelect(report.periodKey) },
                onRetry = { onRetry(report) },
            )
        }
        if (selected != null) {
            item(key = "detail-${selected.periodKey}") {
                ReportDetail(
                    report = selected,
                    budgetCents = budgetCents,
                    categoryDetail = categoryDetail,
                    onSelectCategory = { share, isOther, dayCount ->
                        onSelectCategory(selected, share, isOther, dayCount)
                    },
                    onClearCategory = onClearCategory,
                    onDrill = { tagId -> onDrillToTransactions(selected, tagId) },
                )
            }
        }
    }
}

/** 单份报告行：期键标题 + 状态标识 + 净结余与首条洞察；失败条目附「重试」按钮。 */
@Composable
private fun ReportRow(
    report: Report,
    selected: Boolean,
    generating: Boolean,
    onClick: () -> Unit,
    onRetry: () -> Unit,
) {
    val net = report.incomeCents - report.expenseCents
    val insightTitle = report.localInsights.firstOrNull()?.title
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.secondaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = report.periodKey,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                StatusBadge(report.status)
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = buildString {
                    append("净结余 ${formatCents(net)}")
                    if (insightTitle != null) {
                        append(" · ")
                        append(insightTitle)
                    }
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (report.status == ReportStatus.FAILED || report.status == ReportStatus.STALE) {
                Spacer(modifier = Modifier.height(8.dp))
                RetryButton(generating = generating, onClick = onRetry)
            }
        }
    }
}

/** 报告状态徽标：成功 / 失败 / 数据已变更。 */
@Composable
private fun StatusBadge(status: ReportStatus) {
    val text = when (status) {
        ReportStatus.SUCCESS -> "已生成"
        ReportStatus.FAILED -> "生成失败"
        ReportStatus.STALE -> "数据已变更"
    }
    val color = when (status) {
        ReportStatus.SUCCESS -> MaterialTheme.colorScheme.primary
        ReportStatus.FAILED -> MaterialTheme.colorScheme.error
        ReportStatus.STALE -> MaterialTheme.colorScheme.tertiary
    }
    Surface(shape = MaterialTheme.shapes.small, color = color.copy(alpha = 0.12f)) {
        Text(
            text = text,
            color = color,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}

/** 重试按钮：生成期间禁用并显示进度圈。 */
@Composable
private fun RetryButton(generating: Boolean, onClick: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Button(onClick = onClick, enabled = !generating) {
            if (generating) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            } else {
                Text("重试")
            }
        }
    }
}

/**
 * 报告详情：概览、环比、预算、可交互分类饼图与详情、本地洞察、可选 AI 点评。
 */
@Composable
private fun ReportDetail(
    report: Report,
    budgetCents: Long,
    categoryDetail: CategoryDetailUiState?,
    onSelectCategory: (CategoryShare, Boolean, Int) -> Unit,
    onClearCategory: () -> Unit,
    onDrill: (Long?) -> Unit,
) {
    val net = report.incomeCents - report.expenseCents
    val dayCount = remember(report.windowStartMs, report.windowEndMs) {
        windowDayCount(report.windowStartMs, report.windowEndMs)
    }
    val dailyAvg = if (dayCount > 0) report.expenseCents / dayCount else 0L
    val sharesForChart = remember(report.topCategories, report.expenseCents) {
        buildSharesForChart(report.topCategories, report.expenseCents)
    }
    var selectedIndex by remember(report.periodKey) { mutableStateOf<Int?>(null) }

    fun selectIndex(index: Int) {
        selectedIndex = index
        val share = sharesForChart.getOrNull(index) ?: return
        val isOther = share.tagName == "其他"
        onSelectCategory(share, isOther, dayCount)
    }

    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("概览", style = MaterialTheme.typography.titleSmall)
            DetailLine("收入", formatCents(report.incomeCents))
            DetailLine("支出", formatCents(report.expenseCents))
            DetailLine("净结余", formatCents(net))
            DetailLine("日均支出", formatCents(dailyAvg))

            Text("环比", style = MaterialTheme.typography.titleSmall)
            DetailLine("收入环比", momDelta(report.incomeCents, report.prevIncomeCents))
            DetailLine("支出环比", momDelta(report.expenseCents, report.prevExpenseCents))

            if (budgetCents > 0L &&
                (report.periodType == ReportPeriodType.MONTHLY || report.periodType == ReportPeriodType.WEEKLY)
            ) {
                BudgetBlock(report = report, budgetCents = budgetCents)
            }

            Text("支出分类占比", style = MaterialTheme.typography.titleSmall)
            CategoryShares(
                shares = sharesForChart,
                selectedIndex = selectedIndex,
                onShareClick = { index -> selectIndex(index) },
            )
            if (report.expenseCents > 0 && sharesForChart.isNotEmpty()) {
                PieChart(
                    shares = sharesForChart,
                    totalCents = report.expenseCents,
                    selectedIndex = selectedIndex,
                    onShareSelected = { index -> selectIndex(index) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            categoryDetail?.let { detail ->
                CategoryDetailPanel(
                    detail = detail,
                    onDrill = {
                        onDrill(detail.drillTagId)
                    },
                    onRemainingClick = { rem ->
                        selectedIndex = null
                        onSelectCategory(rem, false, dayCount)
                    },
                    onClear = {
                        selectedIndex = null
                        onClearCategory()
                    },
                )
            }

            if (report.localInsights.isNotEmpty()) {
                Text("本地洞察", style = MaterialTheme.typography.titleSmall)
                report.localInsights.forEach { insight ->
                    InsightCard(insight)
                }
            }

            report.analysisText?.let { text ->
                Text("AI 点评", style = MaterialTheme.typography.titleSmall)
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** TopN + 合成「其他」桶，供列表与饼图共用。 */
private fun buildSharesForChart(
    topCategories: List<CategoryShare>,
    expenseCents: Long,
): List<CategoryShare> {
    if (topCategories.isEmpty() || expenseCents <= 0L) return topCategories
    val topSum = topCategories.sumOf { it.cents }
    return if (topSum < expenseCents) {
        val otherCents = expenseCents - topSum
        val otherPercent = (otherCents * 100 / expenseCents).toInt()
        topCategories + CategoryShare(
            tagName = "其他",
            cents = otherCents,
            percent = otherPercent,
        )
    } else {
        topCategories
    }
}

/** 选中分类详情：金额 / 笔数 / 日均 / 最近流水 / 查看流水。 */
@Composable
private fun CategoryDetailPanel(
    detail: CategoryDetailUiState,
    onDrill: () -> Unit,
    onRemainingClick: (CategoryShare) -> Unit,
    onClear: () -> Unit,
) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = detail.label,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onClear) { Text("取消选中") }
            }
            if (detail.loading) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                }
                return@Column
            }
            detail.errorMessage?.let { msg ->
                Text(msg, color = MaterialTheme.colorScheme.error)
                return@Column
            }
            DetailLine("金额", formatCents(detail.expenseCents))
            DetailLine("占比", "${detail.percent}%")
            DetailLine("笔数", "${detail.txCount}")
            DetailLine("日均", formatCents(detail.dailyAvgCents))
            if (detail.isOtherBucket) {
                Text(
                    text = "「查看流水」将打开本期内全部交易（不按标签过滤）",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (detail.remainingShares.isNotEmpty()) {
                Text("其余分类", style = MaterialTheme.typography.labelLarge)
                detail.remainingShares.forEach { rem ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onRemainingClick(rem) }
                            .padding(vertical = 4.dp),
                    ) {
                        Text(
                            text = rem.tagName ?: "未分类",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = "${rem.percent}% · ${formatCents(rem.cents)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            if (detail.recent.isNotEmpty()) {
                Text("最近流水", style = MaterialTheme.typography.labelLarge)
                detail.recent.forEach { tx ->
                    RecentTxRow(tx)
                }
            } else if (!detail.loading) {
                Text(
                    text = "该分类暂无流水",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Button(
                onClick = onDrill,
                modifier = Modifier.align(Alignment.End),
            ) {
                Text("查看流水")
            }
        }
    }
}

@Composable
private fun RecentTxRow(tx: RecentTransaction) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = tx.tagName ?: (tx.note?.takeIf { it.isNotBlank() } ?: "未分类"),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = formatTime(tx.occurredAt),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = formatCents(tx.amountCents),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

/** 月报预算进度 / 周报「本周可用额度」。 */
@Composable
private fun BudgetBlock(report: Report, budgetCents: Long) {
    Text("预算", style = MaterialTheme.typography.titleSmall)
    when (report.periodType) {
        ReportPeriodType.MONTHLY -> {
            DetailLine("月度预算", formatCents(budgetCents))
            DetailLine("本期支出", formatCents(report.expenseCents))
            val remaining = (budgetCents - report.expenseCents).coerceAtLeast(0L)
            DetailLine("剩余额度", formatCents(remaining))
        }
        ReportPeriodType.WEEKLY -> {
            val daysInMonth = remember(report.windowStartMs) {
                Instant.ofEpochMilli(report.windowStartMs)
                    .atZone(ZoneId.systemDefault())
                    .toLocalDate()
                    .lengthOfMonth()
                    .coerceAtLeast(1)
            }
            val weeklyQuota = budgetCents * 7L / daysInMonth
            DetailLine("本周可用额度", formatCents(weeklyQuota))
            DetailLine("本周已花", formatCents(report.expenseCents))
            val remaining = (weeklyQuota - report.expenseCents).coerceAtLeast(0L)
            DetailLine("本周剩余", formatCents(remaining))
        }
        ReportPeriodType.ANNUAL -> Unit
    }
}

@Composable
private fun InsightCard(insight: LocalInsight) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(insight.title, style = MaterialTheme.typography.titleSmall)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = insight.detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** 详情中的「标签-值」一行。 */
@Composable
private fun DetailLine(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(text = value, style = MaterialTheme.typography.bodyMedium)
    }
}

/** 可点选分类占比列表（与饼图色点、选中态同步）。 */
@Composable
private fun CategoryShares(
    shares: List<CategoryShare>,
    selectedIndex: Int?,
    onShareClick: (Int) -> Unit,
) {
    if (shares.isEmpty()) {
        Text(
            text = "暂无分类数据",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        shares.forEachIndexed { index, share ->
            val selected = index == selectedIndex
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onShareClick(index) }
                    .then(
                        if (selected) {
                            Modifier
                                .background(
                                    MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f),
                                    RoundedCornerShape(8.dp),
                                )
                                .padding(horizontal = 8.dp, vertical = 6.dp)
                        } else {
                            Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                        },
                    ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Canvas(modifier = Modifier.size(10.dp)) {
                    drawCircle(color = PIE_COLORS[index % PIE_COLORS.size])
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = share.tagName ?: "未分类",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "${share.percent}% · ${formatCents(share.cents)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** 加载态占位。 */
@Composable
private fun LoadingState() {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("加载中…", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** 空列表占位：提示打开时自动汇总本期。 */
@Composable
private fun EmptyState() {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "暂无历史报告；本期会在打开时自动汇总",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

/** 环比文案：无上期数据 / 持平 / 涨跌幅百分比。 */
private fun momDelta(current: Long, previous: Long): String {
    if (previous <= 0L) return "无上期数据"
    val diff = current - previous
    if (diff == 0L) return "与上期持平"
    val ratio = abs(diff) * 100.0 / previous
    val sign = if (diff > 0) "+" else "-"
    return String.format(Locale.US, "%s%.1f%%", sign, ratio)
}

private fun windowDayCount(startMs: Long, endMs: Long): Int {
    val zone = ZoneId.systemDefault()
    val start = Instant.ofEpochMilli(startMs).atZone(zone).toLocalDate()
    val end = Instant.ofEpochMilli(endMs).atZone(zone).toLocalDate()
    return ChronoUnit.DAYS.between(start, end).toInt().coerceAtLeast(1)
}
