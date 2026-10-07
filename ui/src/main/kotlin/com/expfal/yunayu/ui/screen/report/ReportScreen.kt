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
import androidx.compose.material3.LinearProgressIndicator
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
import com.expfal.yunayu.domain.model.MonthlyBudgetSnapshot
import com.expfal.yunayu.domain.report.ReportAllowanceCopy
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
import com.expfal.yunayu.domain.util.TimeWindows
import com.expfal.yunayu.ui.component.PIE_COLORS
import com.expfal.yunayu.ui.component.PieChart
import com.expfal.yunayu.ui.util.formatCents
import java.time.LocalDate

/**
 * 「分析报告」列表：周/月切换 + 期键摘要行；点行进入独立详情页。
 *
 * [onOpenDetail]：打开详情全屏；[onSetupBudget]：无预算引导去设置。
 */
@Composable
fun ReportScreen(
    onBack: () -> Unit,
    onOpenDetail: (periodKey: String) -> Unit,
    onSetupBudget: () -> Unit = {},
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
                    periodType = uiState.periodType,
                    selectedPeriodKey = uiState.selectedPeriodKey,
                    generating = uiState.generating,
                    budgetCents = uiState.budgetCents,
                    budgetSnapshot = uiState.budgetSnapshot,
                    onSelect = { key ->
                        viewModel.openDetail(key)
                        onOpenDetail(key)
                    },
                    onRetry = viewModel::retry,
                )
            }
        }
    }
}

/**
 * 报告详情全屏：额度 Hero → 故事 → 建议 → 趋势/分类/细节/深读。
 */
@Composable
fun ReportDetailScreen(
    onBack: () -> Unit,
    onDrillToTransactions: (startMs: Long, endMs: Long, tagId: Long?) -> Unit = { _, _, _ -> },
    onSetupBudget: () -> Unit = {},
    viewModel: ReportViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    BackHandler(onBack = onBack)

    LaunchedEffect(uiState.selectedPeriodKey) {
        val key = uiState.selectedPeriodKey
        if (key != null && uiState.series == null && !uiState.seriesLoading) {
            viewModel.openDetail(key)
        }
    }

    LaunchedEffect(uiState.deepReadMessage) {
        val msg = uiState.deepReadMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(msg)
        viewModel.consumeDeepReadMessage()
    }

    val report = uiState.reports.firstOrNull { it.periodKey == uiState.selectedPeriodKey }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = report?.periodKey
                            ?: uiState.selectedPeriodKey
                            ?: "报告详情",
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        if (report == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                Text("报告不存在或已切换周期", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                item(key = "detail-${report.periodKey}") {
                    ReportDetail(
                        report = report,
                        categoryDetail = uiState.categoryDetail,
                        series = uiState.series,
                        seriesLoading = uiState.seriesLoading,
                        deepReadAvailable = uiState.deepReadAvailable,
                        deepReading = uiState.deepReading,
                        budgetCents = uiState.budgetCents,
                        budgetSnapshot = uiState.budgetSnapshot,
                        onSelectCategory = viewModel::selectCategoryShare,
                        onClearCategory = viewModel::clearCategoryDetail,
                        onDeepRead = { viewModel.deepRead(report) },
                        onSetupBudget = onSetupBudget,
                        onDrill = { tagId ->
                            onDrillToTransactions(report.windowStartMs, report.windowEndMs, tagId)
                        },
                    )
                }
                item { Spacer(modifier = Modifier.height(24.dp)) }
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

/** 报告列表：仅期键摘要行，点选进详情。 */
@Composable
private fun ReportList(
    reports: List<Report>,
    periodType: ReportPeriodType,
    selectedPeriodKey: String?,
    generating: Boolean,
    budgetCents: Long,
    budgetSnapshot: MonthlyBudgetSnapshot?,
    onSelect: (String) -> Unit,
    onRetry: (Report) -> Unit,
) {
    val today = remember { LocalDate.now() }
    val currentPeriodKey = remember(periodType, today) {
        when (periodType) {
            ReportPeriodType.WEEKLY -> TimeWindows.weekPeriodKey(today)
            ReportPeriodType.MONTHLY -> TimeWindows.monthPeriodKey(today)
            ReportPeriodType.ANNUAL -> TimeWindows.yearPeriodKey(today.year)
        }
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(reports, key = { it.periodKey }) { report ->
            ReportRow(
                report = report,
                selected = report.periodKey == selectedPeriodKey,
                generating = generating,
                showLiveAllowance = report.periodKey == currentPeriodKey &&
                    budgetCents > 0L &&
                    budgetSnapshot != null,
                budgetSnapshot = budgetSnapshot,
                onClick = { onSelect(report.periodKey) },
                onRetry = { onRetry(report) },
            )
        }
    }
}

/** 单份报告行：期键 + 净结余；仅「本期」行附带今日还可花。 */
@Composable
private fun ReportRow(
    report: Report,
    selected: Boolean,
    generating: Boolean,
    showLiveAllowance: Boolean,
    budgetSnapshot: MonthlyBudgetSnapshot?,
    onClick: () -> Unit,
    onRetry: () -> Unit,
) {
    val net = report.incomeCents - report.expenseCents
    val summary = buildString {
        if (showLiveAllowance && budgetSnapshot != null) {
            when (report.periodType) {
                ReportPeriodType.WEEKLY -> {
                    append("本周还可花 ${formatCents(budgetSnapshot.weeklyRemainingCents)}")
                    append(" · ")
                }
                ReportPeriodType.MONTHLY -> {
                    append("本月还可花 ${formatCents(budgetSnapshot.remainingCents)}")
                    append(" · ")
                }
                ReportPeriodType.ANNUAL -> Unit
            }
        }
        append("净结余 ${formatCents(net)}")
    }
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
                text = summary,
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
 * 报告详情内容：额度 Hero → 故事 → 建议 → 趋势 / 分类 / 细节 / 深读。
 */
@Composable
private fun ReportDetail(
    report: Report,
    categoryDetail: CategoryDetailUiState?,
    series: ReportSeriesSnapshot?,
    seriesLoading: Boolean,
    deepReadAvailable: Boolean,
    deepReading: Boolean,
    budgetCents: Long,
    budgetSnapshot: MonthlyBudgetSnapshot?,
    onSelectCategory: (CategoryShare, Boolean) -> Unit,
    onClearCategory: () -> Unit,
    onDeepRead: () -> Unit,
    onSetupBudget: () -> Unit,
    onDrill: (Long?) -> Unit,
) {
    val sharesForChart = remember(report.topCategories, report.expenseCents) {
        buildSharesForChart(report.topCategories, report.expenseCents)
    }
    var selectedIndex by remember(report.periodKey) { mutableStateOf<Int?>(null) }
    var detailsExpanded by remember(report.periodKey) { mutableStateOf(false) }

    val story = report.localInsights.firstOrNull { it.kind == LocalInsightKind.STORY }
    val allAdvice = report.localInsights.filter { it.kind == LocalInsightKind.ADVICE }
    val advice = if (report.periodType == ReportPeriodType.WEEKLY) {
        allAdvice.take(1)
    } else {
        allAdvice.take(2)
    }
    val adviceTitle = if (report.periodType == ReportPeriodType.WEEKLY) {
        "下周目标"
    } else {
        "行动建议"
    }
    val detailInsights = report.localInsights.filter {
        it.kind != LocalInsightKind.STORY && it.kind != LocalInsightKind.ADVICE
    }
    val storyPrefix = ReportAllowanceCopy.storyPrefix(
        periodType = report.periodType,
        hasBudget = budgetCents > 0L,
        snapshot = budgetSnapshot,
    )
    val storyText = buildString {
        if (storyPrefix != null) append(storyPrefix)
        if (story != null) {
            if (isNotEmpty()) append(' ')
            append(story.detail)
        }
    }.ifBlank { null }

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
        AllowanceHero(
            periodType = report.periodType,
            periodKey = report.periodKey,
            budgetCents = budgetCents,
            snapshot = budgetSnapshot,
            onSetupBudget = onSetupBudget,
        )

        storyText?.let { StoryBlock(it) }

        if (advice.isNotEmpty()) {
            AdviceSection(title = adviceTitle, advice = advice)
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

/** 生活费额度 Hero：周报主数字=本周还可花；月报=本月剩余；无预算引导。 */
@Composable
private fun AllowanceHero(
    periodType: ReportPeriodType,
    periodKey: String,
    budgetCents: Long,
    snapshot: MonthlyBudgetSnapshot?,
    onSetupBudget: () -> Unit,
) {
    val hasBudget = budgetCents > 0L && snapshot != null
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
            val snap = snapshot
            if (!hasBudget || snap == null) {
                Text(
                    text = ReportAllowanceCopy.statusLine(false, null),
                    style = MaterialTheme.typography.titleMedium,
                )
                TextButton(onClick = onSetupBudget) { Text("去设置") }
                return@Column
            }
            val primaryLabel = if (periodType == ReportPeriodType.MONTHLY) {
                "本月还可花"
            } else {
                "本周还可花"
            }
            val primaryCents = if (periodType == ReportPeriodType.MONTHLY) {
                snap.remainingCents
            } else {
                snap.weeklyRemainingCents
            }
            val overWeek = snap.spentThisWeekCents > snap.weeklyQuotaCents &&
                snap.weeklyQuotaCents > 0L
            Text(
                text = primaryLabel,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = formatCents(primaryCents),
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.SemiBold,
                color = if (overWeek) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.primary
                },
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "本月还剩 ¥${formatCents(snap.remainingCents)} · ${snap.remainingDays} 天",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val daily = ReportAllowanceCopy.dailySpendableCents(snap)
            Text(
                text = "日均可花 ¥${formatCents(daily)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (snap.weeklyQuotaCents > 0L) {
                Spacer(modifier = Modifier.height(8.dp))
                val weekRatio = (snap.spentThisWeekCents.toFloat() / snap.weeklyQuotaCents.toFloat())
                    .coerceIn(0f, 1.2f)
                LinearProgressIndicator(
                    progress = { weekRatio.coerceIn(0f, 1f) },
                    color = if (overWeek) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = "本周已花 ¥${formatCents(snap.spentThisWeekCents)} / 额度 ¥${formatCents(snap.weeklyQuotaCents)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = ReportAllowanceCopy.statusLine(true, snap),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
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

/** 建议区：周报称「下周目标」，月报称「行动建议」。 */
@Composable
private fun AdviceSection(title: String, advice: List<LocalInsight>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title,
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
