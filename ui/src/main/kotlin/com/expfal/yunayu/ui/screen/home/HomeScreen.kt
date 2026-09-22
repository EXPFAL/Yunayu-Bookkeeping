package com.expfal.yunayu.ui.screen.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.expfal.yunayu.ui.screen.accountmanage.AccountManageScreen
import com.expfal.yunayu.ui.screen.apiconfig.ApiSettingsScreen
import com.expfal.yunayu.ui.screen.budget.BudgetCard
import com.expfal.yunayu.ui.screen.budget.MonthlyBudgetSheet
import com.expfal.yunayu.ui.screen.budget.MonthlyBudgetUiState
import com.expfal.yunayu.ui.screen.budget.MonthlyBudgetViewModel
import com.expfal.yunayu.ui.screen.quickadd.QuickAddScreen
import com.expfal.yunayu.ui.screen.report.ReportScreen
import com.expfal.yunayu.ui.screen.subscription.SubscriptionManageScreen
import com.expfal.yunayu.ui.screen.tagmanage.TagManageScreen
import com.expfal.yunayu.ui.screen.transactionmanage.TransactionManageScreen
import kotlinx.coroutines.launch

/** FAB 上边框与持有资金卡片下边框的间距。 */
private val FAB_GAP_DP = 8.dp

/** 标准 FAB 边长。放在固定高度槽里，避免等卡片测高后才出现。 */
private val FAB_SIZE_DP = 56.dp

/** 全屏盖页滑动时长。 */
private const val PAGE_TRANSITION_MILLIS = 300

/** 被盖住的页面只顺着盖页方向挪这么多，保持不透明。 */
private const val PAGE_NUDGE_PERCENT = 8

/** 全屏页。栈底是首页，栈顶是当前页。 */
private enum class FullScreen {
    NONE,
    TAG_MANAGE,
    API_SETTINGS,
    REPORT,
    TRANSACTIONS,
    ACCOUNT_MANAGE,
    SUBSCRIPTION_MANAGE,
    QUICK_ADD,
}

/** 首页：月度预算看板卡片置顶，下方最近记录列表，悬浮「快速记账」按钮进入全屏记账页面。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(modifier: Modifier = Modifier) {
    var showBudgetSetup by remember { mutableStateOf(false) }
    var pageStack by remember { mutableStateOf(listOf(FullScreen.NONE)) }
    var pendingFullScreen by remember { mutableStateOf<FullScreen?>(null) }
    var drillStartMs by remember { mutableStateOf<Long?>(null) }
    var drillEndMs by remember { mutableStateOf<Long?>(null) }
    var drillTagIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    val budgetViewModel: MonthlyBudgetViewModel = viewModel()
    val budgetState by budgetViewModel.uiState.collectAsStateWithLifecycle()
    val homeViewModel: HomeViewModel = viewModel()
    val homeState by homeViewModel.uiState.collectAsStateWithLifecycle()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    // F3 修复：提升 listState 到 when 分发之外，跨全屏切换存活，避免返回时滚动位置丢失
    val listState = rememberLazyListState()

    LaunchedEffect(drawerState) {
        snapshotFlow { drawerState.currentValue to pendingFullScreen }
            .collect { (value, pending) ->
                if (value == DrawerValue.Closed && pending != null && pageStack.last() == FullScreen.NONE) {
                    pendingFullScreen = null
                    pageStack = pageStack + pending
                }
            }
    }

    val popPage: () -> Unit = {
        if (pageStack.size > 1) {
            val leaving = pageStack.last()
            pageStack = pageStack.dropLast(1)
            if (leaving == FullScreen.TRANSACTIONS) {
                drillStartMs = null
                drillEndMs = null
                drillTagIds = emptySet()
            }
        }
    }

    AnimatedContent(
        targetState = pageStack,
        modifier = modifier
            .fillMaxSize()
            .clipToBounds(),
        transitionSpec = {
            val pushing = targetState.size > initialState.size
            val fromRight = if (pushing) {
                targetState.last() == FullScreen.QUICK_ADD
            } else {
                initialState.last() == FullScreen.QUICK_ADD
            }
            val slide = tween<IntOffset>(PAGE_TRANSITION_MILLIS, easing = FastOutSlowInEasing)
            val nudge: (Int) -> Int = { fullWidth -> fullWidth * PAGE_NUDGE_PERCENT / 100 }
            val enter = if (pushing) {
                slideInHorizontally(slide) { fullWidth -> if (fromRight) fullWidth else -fullWidth }
            } else {
                slideInHorizontally(slide) { fullWidth -> if (fromRight) -nudge(fullWidth) else nudge(fullWidth) }
            }
            val exit = if (pushing) {
                slideOutHorizontally(slide) { fullWidth -> if (fromRight) -nudge(fullWidth) else nudge(fullWidth) }
            } else {
                slideOutHorizontally(slide) { fullWidth -> if (fromRight) fullWidth else -fullWidth }
            }
            (enter togetherWith exit).apply {
                targetContentZIndex = if (pushing) 1f else 0f
            }.using(SizeTransform(clip = true) { _, _ -> snap() })
        },
        label = "fullScreen",
    ) { stack ->
        val screen = stack.last()
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            when (screen) {
                FullScreen.TAG_MANAGE -> TagManageScreen(onBack = popPage)
                FullScreen.API_SETTINGS -> ApiSettingsScreen(onBack = popPage)
                FullScreen.REPORT -> ReportScreen(
                    onBack = popPage,
                    onDrillToTransactions = { start, end, tagId ->
                        drillStartMs = start
                        drillEndMs = end
                        drillTagIds = tagId?.let { setOf(it) } ?: emptySet()
                        pageStack = pageStack + FullScreen.TRANSACTIONS
                    },
                )
                FullScreen.TRANSACTIONS -> TransactionManageScreen(
                    onBack = popPage,
                    initialStartMs = drillStartMs,
                    initialEndMs = drillEndMs,
                    initialTagIds = drillTagIds,
                )
                FullScreen.ACCOUNT_MANAGE -> AccountManageScreen(onBack = popPage)
                FullScreen.SUBSCRIPTION_MANAGE -> SubscriptionManageScreen(onBack = popPage)
                FullScreen.QUICK_ADD -> QuickAddScreen(
                    onBack = popPage,
                    onSaved = {
                        popPage()
                        homeViewModel.notifySaved()
                    },
                )
                FullScreen.NONE -> HomeMainContent(
                    modifier = Modifier.fillMaxSize(),
                    homeViewModel = homeViewModel,
                    homeState = homeState,
                    budgetState = budgetState,
                    drawerState = drawerState,
                    scope = scope,
                    listState = listState,
                    onDrawerDestination = { pendingFullScreen = it },
                    onShowQuickAdd = { pageStack = pageStack + FullScreen.QUICK_ADD },
                    onShowBudgetSetup = { showBudgetSetup = true },
                )
            }
        }
    }

    if (showBudgetSetup) {
        MonthlyBudgetSheet(
            viewModel = budgetViewModel,
            onDismissRequest = { showBudgetSetup = false },
        )
    }
}

/** 首页主内容区：侧栏抽屉 + Scaffold + 预算卡片 + 最近记录 + FAB。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeMainContent(
    modifier: Modifier,
    homeViewModel: HomeViewModel,
    homeState: HomeUiState,
    budgetState: MonthlyBudgetUiState,
    drawerState: androidx.compose.material3.DrawerState,
    scope: kotlinx.coroutines.CoroutineScope,
    listState: LazyListState,
    onDrawerDestination: (FullScreen) -> Unit,
    onShowQuickAdd: () -> Unit,
    onShowBudgetSetup: () -> Unit,
) {
    LaunchedEffect(homeViewModel) {
        homeViewModel.events.collect { event ->
            when (event) {
                HomeEvent.Saved -> listState.animateScrollToItem(0)
            }
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            HomeDrawerContent(
                onItemClick = { selectedFullScreen ->
                    onDrawerDestination(selectedFullScreen)
                    scope.launch { drawerState.close() }
                },
            )
        },
    ) {
        Scaffold(
            modifier = modifier,
            topBar = {
                TopAppBar(
                    title = { Text("首页") },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(
                                imageVector = Icons.Default.Menu,
                                contentDescription = "菜单",
                            )
                        }
                    },
                )
            },
        ) { innerPadding ->
            HomeContent(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(top = 4.dp),
                homeState = homeState,
                budgetState = budgetState,
                listState = listState,
                onShowQuickAdd = onShowQuickAdd,
                onShowBudgetSetup = onShowBudgetSetup,
            )
        }
    }
}

/** 首页主内容区：预算卡片 + 持有资金 + FAB + 最近记录。 */
@Composable
private fun HomeContent(
    modifier: Modifier,
    homeState: HomeUiState,
    budgetState: MonthlyBudgetUiState,
    listState: LazyListState,
    onShowQuickAdd: () -> Unit,
    onShowBudgetSetup: () -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 8.dp),
    ) {
        BudgetCard(
            loading = budgetState.loading,
            budgetCents = budgetState.budgetCents,
            snapshot = budgetState.snapshot,
            onEdit = onShowBudgetSetup,
            onSetup = onShowBudgetSetup,
        )
        Spacer(modifier = Modifier.height(6.dp))
        HeldFundsCard(
            heldCents = homeState.heldCents,
            heldByAccount = homeState.heldByAccount,
        )
        QuickAddFabSlot(onShowQuickAdd = onShowQuickAdd)
        if (!budgetState.loading && homeState.accountsReady) {
            if (!homeState.loading && homeState.recent.isEmpty()) {
                FirstRunHint()
                Spacer(modifier = Modifier.height(6.dp))
            }
            RecentTransactionsSection(
                homeState = homeState,
                listState = listState,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** 记一笔按钮的固定槽：高度为间距加 FAB 边长，不依赖卡片测量。 */
@Composable
private fun QuickAddFabSlot(onShowQuickAdd: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(FAB_GAP_DP + FAB_SIZE_DP),
    ) {
        FloatingActionButton(
            onClick = onShowQuickAdd,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = FAB_GAP_DP),
        ) {
            Icon(imageVector = Icons.Default.Add, contentDescription = "快速记账")
        }
    }
}

/** 最近记录区域：紧挨记一笔按钮槽，不再用负向偏移去贴齐按钮。 */
@Composable
private fun RecentTransactionsSection(
    homeState: HomeUiState,
    listState: LazyListState,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            "最近记录",
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(Modifier.height(4.dp))
        RecentTransactionsCard(
            uiState = homeState,
            modifier = Modifier.weight(1f),
            listState = listState,
        )
    }
}

/** 首次引导横幅：无任何交易记录时提示「点右侧 +，3 秒记一笔」，记下第一笔后随列表非空自动消失。 */
@Composable
private fun FirstRunHint(modifier: Modifier = Modifier) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = modifier.fillMaxWidth(),
    ) {
        Text(
            "点右侧 +，3 秒记一笔",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        )
    }
}

/** 侧栏抽屉：日常功能在前，设置在最后。文案与各页顶栏一致。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeDrawerContent(
    onItemClick: (FullScreen) -> Unit,
    modifier: Modifier = Modifier,
) {
    ModalDrawerSheet(modifier = modifier.fillMaxWidth(0.45f)) {
        Text(
            text = "功能菜单",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(16.dp),
        )
        DrawerEntry(Icons.AutoMirrored.Filled.List, "收支管理") { onItemClick(FullScreen.TRANSACTIONS) }
        DrawerEntry(Icons.Filled.DateRange, "分析报告") { onItemClick(FullScreen.REPORT) }
        DrawerEntry(Icons.Filled.Notifications, "订阅开支") { onItemClick(FullScreen.SUBSCRIPTION_MANAGE) }
        DrawerEntry(Icons.Filled.AccountCircle, "账户管理") { onItemClick(FullScreen.ACCOUNT_MANAGE) }
        DrawerEntry(Icons.Filled.Star, "标签管理") { onItemClick(FullScreen.TAG_MANAGE) }
        DrawerEntry(Icons.Filled.Settings, "API 设置") { onItemClick(FullScreen.API_SETTINGS) }
    }
}

@Composable
private fun DrawerEntry(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    NavigationDrawerItem(
        icon = { Icon(icon, contentDescription = null) },
        label = { Text(label) },
        selected = false,
        onClick = onClick,
    )
}
