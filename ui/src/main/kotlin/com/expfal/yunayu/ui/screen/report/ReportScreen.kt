@file:OptIn(ExperimentalMaterial3Api::class)

package com.expfal.yunayu.ui.screen.report

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.expfal.yunayu.domain.report.model.CategoryShare
import com.expfal.yunayu.domain.report.model.Report
import com.expfal.yunayu.domain.report.model.ReportPeriodType
import com.expfal.yunayu.domain.report.model.ReportStatus
import com.expfal.yunayu.ui.component.PieChart
import com.expfal.yunayu.ui.util.formatCents
import java.util.Locale

/**
 * 「分析报告」全屏：顶部月度/年度切换，中部按期键倒序的报告列表，点选展开详情；失败条目可重试。
 *
 * 列表与详情同处一个 [LazyColumn]，选中详情作为尾随 item 追加，保证整体可滚动。
 */
@Composable
fun ReportScreen(
    onBack: () -> Unit,
    viewModel: ReportViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
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
                    onSelect = viewModel::selectReport,
                    onRetry = viewModel::retry,
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
    onSelect: (String) -> Unit,
    onRetry: (Report) -> Unit,
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
                ReportDetail(report = selected)
            }
        }
    }
}

/** 单份报告行：期键标题 + 状态标识 + 收支摘要；失败条目附「重试」按钮。 */
@Composable
private fun ReportRow(
    report: Report,
    selected: Boolean,
    generating: Boolean,
    onClick: () -> Unit,
    onRetry: () -> Unit,
) {
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
                text = "收入 ${formatCents(report.incomeCents)} · 支出 ${formatCents(report.expenseCents)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (report.status == ReportStatus.FAILED) {
                Spacer(modifier = Modifier.height(8.dp))
                RetryButton(generating = generating, onClick = onRetry)
            }
        }
    }
}

/** 报告状态徽标：成功 / 失败，用主题色弱化背景衬托。 */
@Composable
private fun StatusBadge(status: ReportStatus) {
    val text = when (status) {
        ReportStatus.SUCCESS -> "已生成"
        ReportStatus.FAILED -> "生成失败"
    }
    val color = when (status) {
        ReportStatus.SUCCESS -> MaterialTheme.colorScheme.primary
        ReportStatus.FAILED -> MaterialTheme.colorScheme.error
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

/** 报告详情：收支与环比、分类占比、饼状图、分析文本（null 时按状态给占位）。 */
@Composable
private fun ReportDetail(report: Report) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("详情", style = MaterialTheme.typography.titleSmall)
            DetailLine("收入", formatCents(report.incomeCents))
            DetailLine("支出", formatCents(report.expenseCents))
            DetailLine("环比支出", expenseDelta(report))
            Text("支出分类占比", style = MaterialTheme.typography.titleSmall)
            CategoryShares(report.topCategories)
            // 饼状图：总支出 > 0 且有分类数据时显示
            if (report.expenseCents > 0 && report.topCategories.isNotEmpty()) {
                // 当 topCategories 被截断（<5）时，补齐「其他」桶保证圆环闭合
                val sharesForChart = remember(report.topCategories, report.expenseCents) {
                    val topSum = report.topCategories.sumOf { it.cents }
                    if (topSum < report.expenseCents) {
                        val otherCents = report.expenseCents - topSum
                        val otherPercent = (otherCents * 100 / report.expenseCents).toInt()
                        report.topCategories + CategoryShare(
                            tagName = "其他",
                            cents = otherCents,
                            percent = otherPercent,
                        )
                    } else {
                        report.topCategories
                    }
                }
                PieChart(
                    shares = sharesForChart,
                    totalCents = report.expenseCents,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Text("分析", style = MaterialTheme.typography.titleSmall)
            Text(
                text = analysisText(report),
                style = MaterialTheme.typography.bodyMedium,
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

/** 分类占比列表；为空时给出占位文案。 */
@Composable
private fun CategoryShares(shares: List<CategoryShare>) {
    if (shares.isEmpty()) {
        Text(
            text = "暂无分类数据",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        shares.forEach { share ->
            Row(modifier = Modifier.fillMaxWidth()) {
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

/** 空列表占位：提示应用启动时自动补生成上一周期报告。 */
@Composable
private fun EmptyState() {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "暂无报告，打开应用时会自动补生成上一周期报告",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

/** 分析文本占位：FAILED →「生成失败，可重试」；SUCCESS 但空白 →「暂无文字分析」。 */
private fun analysisText(report: Report): String {
    report.analysisText?.let { return it }
    return if (report.status == ReportStatus.FAILED) "生成失败，可重试" else "暂无文字分析"
}

/** 支出环比文案：无上期数据 / 持平 / 涨跌幅百分比。 */
private fun expenseDelta(report: Report): String {
    val prev = report.prevExpenseCents
    if (prev <= 0L) return "无上期数据"
    val diff = report.expenseCents - prev
    if (diff == 0L) return "与上期持平"
    val ratio = diff * 100.0 / prev
    return String.format(Locale.US, "%+.1f%%", ratio)
}
