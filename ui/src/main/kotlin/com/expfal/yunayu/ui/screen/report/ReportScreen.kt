@file:OptIn(ExperimentalMaterial3Api::class)

package com.expfal.yunayu.ui.screen.report

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Star
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
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.expfal.yunayu.domain.report.model.CategoryChange
import com.expfal.yunayu.domain.report.model.CategoryShare
import com.expfal.yunayu.domain.report.model.LocalInsight
import com.expfal.yunayu.domain.report.model.LocalInsightKind
import com.expfal.yunayu.domain.report.model.MomComparison
import com.expfal.yunayu.domain.report.model.PeriodTotalsPoint
import com.expfal.yunayu.domain.report.model.Report
import com.expfal.yunayu.domain.report.model.ReportPeriodType
import com.expfal.yunayu.domain.report.model.ReportSeriesSnapshot
import com.expfal.yunayu.domain.report.model.ReportStatus
import com.expfal.yunayu.ui.component.PIE_COLORS
import com.expfal.yunayu.ui.component.PieChart
import com.expfal.yunayu.ui.util.formatCents
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * 「分析报告」全屏：顶部周/月切换，中部按期键倒序的报告列表，点选展开详情；失败条目可重试。
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
    val snackbarHostState = remember { SnackbarHostState() }
    BackHandler(onBack = onBack)

    LaunchedEffect(uiState.deepReadMessage) {
        val msg = uiState.deepReadMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(msg)
        viewModel.consumeDeepReadMessage()
    }

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
        snackbarHost = { SnackbarHost(snackbarHostState) },
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
                    categoryDetail = uiState.categoryDetail,
                    series = uiState.series,
                    seriesLoading = uiState.seriesLoading,
                    deepReadAvailable = uiState.deepReadAvailable,
                    deepReading = uiState.deepReading,
                    onSelect = viewModel::selectReport,
                    onRetry = viewModel::retry,
                    onSelectCategory = viewModel::selectCategoryShare,
                    onClearCategory = viewModel::clearCategoryDetail,
                    onDeepRead = viewModel::deepRead,
                    onDrillToTransactions = { report, tagId ->
                        onDrillToTransactions(report.windowStartMs, report.windowEndMs, tagId)
                    },
                )
            }
        }
    }
}

/** 周度/月度切换控件，样式对齐快捷记账的收/支 [FilterChip]。 */
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
    }
}

/** 报告列表：按期键倒序渲染每行，选中报告的详情作为尾随 item 展开。 */
@Composable
private fun ReportList(
    reports: List<Report>,
    selectedPeriodKey: String?,
    generating: Boolean,
    categoryDetail: CategoryDetailUiState?,
    series: ReportSeriesSnapshot?,
    seriesLoading: Boolean,
    deepReadAvailable: Boolean,
    deepReading: Boolean,
    onSelect: (String) -> Unit,
    onRetry: (Report) -> Unit,
    onSelectCategory: (CategoryShare, Boolean) -> Unit,
    onClearCategory: () -> Unit,
    onDeepRead: (Report) -> Unit,
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
                    categoryDetail = categoryDetail,
                    series = series,
                    seriesLoading = seriesLoading,
                    deepReadAvailable = deepReadAvailable,
                    deepReading = deepReading,
                    onSelectCategory = onSelectCategory,
                    onClearCategory = onClearCategory,
                    onDeepRead = { onDeepRead(selected) },
                    onDrill = { tagId -> onDrillToTransactions(selected, tagId) },
                )
            }
        }
    }
}

/** 单份报告行：期键标题 + 状态标识 + 净结余与首条故事/洞察。 */
@Composable
private fun ReportRow(
    report: Report,
    selected: Boolean,
    generating: Boolean,
    onClick: () -> Unit,
    onRetry: () -> Unit,
) {
    val net = report.incomeCents - report.expenseCents
    val insightTitle = report.localInsights
        .firstOrNull { it.kind == LocalInsightKind.STORY }
        ?.title
        ?: report.localInsights.firstOrNull()?.title
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
 * 报告详情：Hero 数字区、故事段、建议条、趋势柱、环比对比、分类增减、饼图、折叠细节、可选深读。
 */
@Composable
private fun ReportDetail(
    report: Report,
    categoryDetail: CategoryDetailUiState?,
    series: ReportSeriesSnapshot?,
    seriesLoading: Boolean,
    deepReadAvailable: Boolean,
    deepReading: Boolean,
    onSelectCategory: (CategoryShare, Boolean) -> Unit,
    onClearCategory: () -> Unit,
    onDeepRead: () -> Unit,
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
    var detailsExpanded by remember(report.periodKey) { mutableStateOf(false) }

    val story = report.localInsights.firstOrNull { it.kind == LocalInsightKind.STORY }
    val advice = report.localInsights.filter { it.kind == LocalInsightKind.ADVICE }
    val detailInsights = report.localInsights.filter {
        it.kind != LocalInsightKind.STORY && it.kind != LocalInsightKind.ADVICE
    }

    fun selectIndex(index: Int) {
        selectedIndex = index
        val share = sharesForChart.getOrNull(index) ?: return
        val isOther = share.tagName == "其他"
        onSelectCategory(share, isOther)
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        HeroSection(
            periodKey = report.periodKey,
            incomeCents = report.incomeCents,
            expenseCents = report.expenseCents,
            netCents = net,
            dailyAvgCents = dailyAvg,
            mom = series?.mom,
            prevNetCents = report.prevIncomeCents - report.prevExpenseCents,
        )

        story?.let { StoryBlock(it.detail) }

        if (advice.isNotEmpty()) {
            AdviceSection(advice)
        }

        if (seriesLoading) {
            Text(
                text = "加载趋势…",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        series?.let { snap ->
            if (snap.points.isNotEmpty()) {
                SectionCard(title = "近 ${snap.points.size} 期支出") {
                    ExpenseTrendChart(snap.points)
                }
                MomCompareSection(snap.mom, report)
                CategoryChangeSection(snap.topIncreases, snap.topDecreases)
            }
        }

        SectionCard(title = "支出分类占比") {
            if (report.expenseCents > 0 && sharesForChart.isNotEmpty()) {
                PieChart(
                    shares = sharesForChart,
                    totalCents = report.expenseCents,
                    selectedIndex = selectedIndex,
                    onShareSelected = { index -> selectIndex(index) },
                    onSelectionCleared = {
                        selectedIndex = null
                        onClearCategory()
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
            CategoryShares(
                shares = sharesForChart,
                selectedIndex = selectedIndex,
                onShareClick = { index -> selectIndex(index) },
            )
        }

        categoryDetail?.let { detail ->
            CategoryDetailPanel(
                detail = detail,
                onDrill = { onDrill(detail.drillTagId) },
                onClear = {
                    selectedIndex = null
                    onClearCategory()
                },
            )
        }

        if (detailInsights.isNotEmpty()) {
            TextButton(onClick = { detailsExpanded = !detailsExpanded }) {
                Text(if (detailsExpanded) "收起细节洞察" else "展开细节洞察（${detailInsights.size}）")
            }
            if (detailsExpanded) {
                detailInsights.forEach { InsightCard(it) }
            }
        }

        report.analysisText?.takeIf { it.isNotBlank() }?.let { text ->
            SectionCard(title = "深度解读") {
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (deepReadAvailable) {
            Button(
                onClick = onDeepRead,
                enabled = !deepReading,
                modifier = Modifier.align(Alignment.End),
            ) {
                if (deepReading) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Text(if (report.analysisText.isNullOrBlank()) "深度解读" else "重新解读")
                }
            }
        }
    }
}

/** Hero 数字区：周期标签 + 净结余主数字 + 收支副行 + 环比徽章。 */
@Composable
private fun HeroSection(
    periodKey: String,
    incomeCents: Long,
    expenseCents: Long,
    netCents: Long,
    dailyAvgCents: Long,
    mom: MomComparison?,
    prevNetCents: Long,
) {
    val netColor = when {
        netCents > 0L -> MaterialTheme.colorScheme.primary
        netCents < 0L -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurface
    }
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp)) {
            Text(
                text = periodKey,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "净结余",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = formatCents(netCents),
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = netColor,
                    modifier = Modifier.weight(1f),
                )
                mom?.let { MoMBadge(it, prevNetCents = prevNetCents) }
            }
            Spacer(modifier = Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                HeroMetric("收入", formatCents(incomeCents))
                HeroMetric("支出", formatCents(expenseCents))
                HeroMetric("日均", formatCents(dailyAvgCents))
            }
        }
    }
}

@Composable
private fun HeroMetric(label: String, value: String) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium,
        )
    }
}

/** 环比徽章：净结余 ±% 优先，否则支出 ±%；以净结余变化方向着色。 */
@Composable
private fun MoMBadge(mom: MomComparison, prevNetCents: Long) {
    val positive = mom.netDeltaCents > 0L
    val neutral = mom.netDeltaCents == 0L
    val bg = when {
        neutral -> MaterialTheme.colorScheme.surfaceVariant
        positive -> MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
        else -> MaterialTheme.colorScheme.error.copy(alpha = 0.14f)
    }
    val fg = when {
        neutral -> MaterialTheme.colorScheme.onSurfaceVariant
        positive -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.error
    }
    val percentText = netDeltaPercent(mom.netDeltaCents, prevNetCents)?.let { signedPercent(it) }
        ?: mom.expenseDeltaPercent?.let { signedPercent(it) }
    val label = if (percentText != null) {
        "较上期 $percentText"
    } else {
        "较上期 ${signedCents(mom.netDeltaCents)}"
    }
    Surface(shape = RoundedCornerShape(50), color = bg) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(
                imageVector = when {
                    neutral -> Icons.Filled.Star
                    positive -> Icons.Filled.KeyboardArrowUp
                    else -> Icons.Filled.KeyboardArrowDown
                },
                contentDescription = null,
                tint = fg,
                modifier = Modifier.size(14.dp),
            )
            Text(text = label, style = MaterialTheme.typography.labelMedium, color = fg)
        }
    }
}

/** 净结余环比百分比；上期净结余为 0 时返回 null。 */
private fun netDeltaPercent(deltaCents: Long, prevNetCents: Long): Int? {
    if (prevNetCents == 0L) return null
    return ((deltaCents * 100) / kotlin.math.abs(prevNetCents)).toInt()
}

/** 本期故事：大字号正文段，左侧主色竖条，非卡片。 */
@Composable
private fun StoryBlock(detail: String) {
    Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .fillMaxHeight()
                .background(
                    color = MaterialTheme.colorScheme.primary,
                    shape = RoundedCornerShape(2.dp),
                ),
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "本期故事",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = detail,
                style = MaterialTheme.typography.bodyLarge,
                lineHeight = MaterialTheme.typography.bodyLarge.lineHeight,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

/** 建议区：行动提示条，区别于洞察卡。 */
@Composable
private fun AdviceSection(advice: List<LocalInsight>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "行动建议",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        advice.forEach { item ->
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.45f),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Star,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.size(18.dp),
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = item.title,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Medium,
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = item.detail,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

/** 统一区块底：浅底圆角容器 + 小标题。 */
@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(modifier = Modifier.height(12.dp))
            content()
        }
    }
}

/** 环比对比：上期→本期 + 差额与迷你 delta 条。 */
@Composable
private fun MomCompareSection(mom: MomComparison, report: Report) {
    SectionCard(title = "环比") {
        MomRow(
            label = "收入",
            previous = report.prevIncomeCents,
            current = report.incomeCents,
            delta = mom.incomeDeltaCents,
            increaseIsGood = true,
        )
        Spacer(modifier = Modifier.height(10.dp))
        MomRow(
            label = "支出",
            previous = report.prevExpenseCents,
            current = report.expenseCents,
            delta = mom.expenseDeltaCents,
            increaseIsGood = false,
            extra = mom.expenseDeltaPercent?.let { signedPercent(it) },
        )
        Spacer(modifier = Modifier.height(10.dp))
        MomRow(
            label = "净结余",
            previous = report.prevIncomeCents - report.prevExpenseCents,
            current = report.incomeCents - report.expenseCents,
            delta = mom.netDeltaCents,
            increaseIsGood = true,
        )
    }
}

@Composable
private fun MomRow(
    label: String,
    previous: Long,
    current: Long,
    delta: Long,
    increaseIsGood: Boolean,
    extra: String? = null,
) {
    val good = when {
        delta > 0L -> increaseIsGood
        delta < 0L -> !increaseIsGood
        else -> true
    }
    val accent = when {
        delta == 0L -> MaterialTheme.colorScheme.outline
        good -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.error
    }
    val maxAbs = maxOf(kotlin.math.abs(previous), kotlin.math.abs(current), 1L)
    val fraction = (kotlin.math.abs(delta).toFloat() / maxAbs.toFloat()).coerceIn(0.08f, 1f)

    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(52.dp),
            )
            Text(
                text = "${formatCents(previous)} → ${formatCents(current)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = buildString {
                    append(signedCents(delta))
                    if (extra != null) append("（$extra）")
                },
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
                color = accent,
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .background(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(2.dp),
                ),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction)
                    .fillMaxHeight()
                    .background(color = accent, shape = RoundedCornerShape(2.dp)),
            )
        }
    }
}

/** 分类增减：Top 增 / Top 减 + mini 条。 */
@Composable
private fun CategoryChangeSection(
    increases: List<CategoryChange>,
    decreases: List<CategoryChange>,
) {
    if (increases.isEmpty() && decreases.isEmpty()) return
    val maxAbs = (
        increases + decreases
        ).maxOfOrNull { kotlin.math.abs(it.deltaCents) }?.coerceAtLeast(1L) ?: 1L

    SectionCard(title = "分类增减") {
        val rows = increases.map { it to true } + decreases.map { it to false }
        rows.forEachIndexed { index, (change, isIncrease) ->
            ChangeRow(change = change, maxAbs = maxAbs, isIncrease = isIncrease)
            if (index != rows.lastIndex) {
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun ChangeRow(change: CategoryChange, maxAbs: Long, isIncrease: Boolean) {
    val accent = if (isIncrease) {
        MaterialTheme.colorScheme.error
    } else {
        MaterialTheme.colorScheme.primary
    }
    val fraction = (kotlin.math.abs(change.deltaCents).toFloat() / maxAbs.toFloat())
        .coerceIn(0.08f, 1f)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = if (isIncrease) {
                Icons.Filled.KeyboardArrowUp
            } else {
                Icons.Filled.KeyboardArrowDown
            },
            contentDescription = null,
            tint = accent,
            modifier = Modifier.size(16.dp),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = change.tagName ?: "未分类",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.width(72.dp),
            maxLines = 1,
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .height(6.dp)
                .background(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(3.dp),
                ),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction)
                    .fillMaxHeight()
                    .background(color = accent, shape = RoundedCornerShape(3.dp)),
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = signedCents(change.deltaCents),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Medium,
            color = accent,
            textAlign = TextAlign.End,
            modifier = Modifier.width(72.dp),
        )
    }
}

/** 近 N 期支出竖柱图：本期高亮，金额在柱上、期键在柱下。 */
@Composable
private fun ExpenseTrendChart(points: List<PeriodTotalsPoint>) {
    val maxExpense = points.maxOf { it.expenseCents }.coerceAtLeast(1L)
    val barColor = MaterialTheme.colorScheme.primary
    val currentColor = MaterialTheme.colorScheme.tertiary
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    // 最后一期视为本期（序列按时间升序）
    val lastIndex = points.lastIndex

    Column {
        Row(modifier = Modifier.fillMaxWidth()) {
            points.forEach { point ->
                Text(
                    text = formatCents(point.expenseCents),
                    style = MaterialTheme.typography.labelSmall,
                    color = labelColor,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(112.dp),
        ) {
            val n = points.size
            val gap = size.width * 0.06f
            val barWidth = (size.width - gap * (n + 1)) / n
            points.forEachIndexed { index, point ->
                val left = gap + index * (barWidth + gap)
                val fraction = (point.expenseCents.toFloat() / maxExpense.toFloat())
                    .coerceIn(0.02f, 1f)
                val barHeight = size.height * fraction
                val top = size.height - barHeight
                drawRoundRect(
                    color = trackColor,
                    topLeft = Offset(left, 0f),
                    size = Size(barWidth, size.height),
                    cornerRadius = CornerRadius(barWidth * 0.25f, barWidth * 0.25f),
                )
                drawRoundRect(
                    color = if (index == lastIndex) currentColor else barColor,
                    topLeft = Offset(left, top),
                    size = Size(barWidth, barHeight),
                    cornerRadius = CornerRadius(barWidth * 0.25f, barWidth * 0.25f),
                )
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            points.forEachIndexed { index, point ->
                Text(
                    text = shortPeriodLabel(point.periodKey),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (index == lastIndex) {
                        MaterialTheme.colorScheme.tertiary
                    } else {
                        labelColor
                    },
                    textAlign = TextAlign.Center,
                    fontWeight = if (index == lastIndex) FontWeight.SemiBold else FontWeight.Normal,
                    modifier = Modifier.weight(1f),
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

/** 选中分类：金额、占比，以及跳到收支管理。 */
@Composable
private fun CategoryDetailPanel(
    detail: CategoryDetailUiState,
    onDrill: () -> Unit,
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
            DetailLine("金额", formatCents(detail.expenseCents))
            DetailLine("占比", "${detail.percent}%")
            if (detail.isOtherBucket) {
                Text(
                    text = "「查看流水」将打开本期内全部交易（不按标签过滤）",
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

/** 细节洞察卡（折叠区复用）。 */
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

private fun windowDayCount(startMs: Long, endMs: Long): Int {
    val zone = ZoneId.systemDefault()
    val start = Instant.ofEpochMilli(startMs).atZone(zone).toLocalDate()
    val end = Instant.ofEpochMilli(endMs).atZone(zone).toLocalDate()
    return ChronoUnit.DAYS.between(start, end).toInt().coerceAtLeast(1)
}

private fun signedCents(delta: Long): String {
    val abs = formatCents(kotlin.math.abs(delta))
    return when {
        delta > 0L -> "+$abs"
        delta < 0L -> "-$abs"
        else -> abs
    }
}

private fun signedPercent(value: Int): String =
    if (value > 0) "+$value%" else "$value%"

private fun shortPeriodLabel(periodKey: String): String =
    when {
        periodKey.contains("-W") -> periodKey.substringAfterLast('-')
        periodKey.length >= 7 -> periodKey.takeLast(5)
        else -> periodKey
    }
