package com.expfal.yunayu.ui.screen.home

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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Menu
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
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
import com.expfal.yunayu.ui.screen.tagmanage.TagManageScreen
import com.expfal.yunayu.ui.screen.transactionmanage.TransactionManageScreen
import kotlinx.coroutines.launch

/** 首页全屏子界面：无 / 标签管理 / API 管理 / 分析报告 / 收支管理 / 账户管理 / 记一笔，七态互斥。 */
private enum class FullScreen { NONE, TAG_MANAGE, API_SETTINGS, REPORT, TRANSACTIONS, ACCOUNT_MANAGE, QUICK_ADD }

/** 首页：月度预算看板卡片置顶，下方最近记录列表，悬浮「快速记账」按钮进入全屏记账页面。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(modifier: Modifier = Modifier) {
    var showBudgetSetup by remember { mutableStateOf(false) }
    var fullScreen by remember { mutableStateOf(FullScreen.NONE) }
    val budgetViewModel: MonthlyBudgetViewModel = viewModel()
    val budgetState by budgetViewModel.uiState.collectAsStateWithLifecycle()
    val homeViewModel: HomeViewModel = viewModel()
    val homeState by homeViewModel.uiState.collectAsStateWithLifecycle()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    // F3 修复：提升 listState 到 when 分发之外，跨全屏切换存活，避免返回时滚动位置丢失
    val listState = rememberLazyListState()

    when (fullScreen) {
        FullScreen.TAG_MANAGE -> TagManageScreen(onBack = { fullScreen = FullScreen.NONE })
        FullScreen.API_SETTINGS -> ApiSettingsScreen(onBack = { fullScreen = FullScreen.NONE })
        FullScreen.REPORT -> ReportScreen(onBack = { fullScreen = FullScreen.NONE })
        FullScreen.TRANSACTIONS -> TransactionManageScreen(onBack = { fullScreen = FullScreen.NONE })
        FullScreen.ACCOUNT_MANAGE -> AccountManageScreen(onBack = { fullScreen = FullScreen.NONE })
        FullScreen.QUICK_ADD -> QuickAddScreen(
            onBack = { fullScreen = FullScreen.NONE },
            onSaved = {
                fullScreen = FullScreen.NONE
                homeViewModel.notifySaved()
            },
        )
        FullScreen.NONE -> HomeMainContent(
            modifier = modifier,
            homeViewModel = homeViewModel,
            homeState = homeState,
            budgetState = budgetState,
            drawerState = drawerState,
            scope = scope,
            listState = listState,
            currentFullScreen = fullScreen,
            onFullScreenChange = { fullScreen = it },
            onShowQuickAdd = { fullScreen = FullScreen.QUICK_ADD },
            onShowBudgetSetup = { showBudgetSetup = true },
        )
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
    currentFullScreen: FullScreen,
    onFullScreenChange: (FullScreen) -> Unit,
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
                currentFullScreen = currentFullScreen,
                onItemClick = { selectedFullScreen ->
                    onFullScreenChange(selectedFullScreen)
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
        // 持有资金卡片与 FAB 右边框对齐
        Box {
            val density = LocalDensity.current
            var cardHeightPx by remember { mutableIntStateOf(0) }
            HeldFundsCard(
                heldCents = homeState.heldCents,
                heldByAccount = homeState.heldByAccount,
                modifier = Modifier.onGloballyPositioned { coordinates ->
                    cardHeightPx = coordinates.size.height
                },
            )
            // FAB 上边框在持有资金下边框以下 8dp，右边框对齐
            FloatingActionButton(
                onClick = onShowQuickAdd,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = with(density) { cardHeightPx.toDp() } + 8.dp),
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = "快速记账")
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        if (!homeState.loading && homeState.recent.isEmpty()) {
            FirstRunHint()
            Spacer(modifier = Modifier.height(6.dp))
        }
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

/** 侧栏抽屉内容：显示「功能菜单」标题和 5 个 NavigationDrawerItem。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeDrawerContent(
    currentFullScreen: FullScreen,
    onItemClick: (FullScreen) -> Unit,
    modifier: Modifier = Modifier,
) {
    ModalDrawerSheet(modifier = modifier.fillMaxWidth(0.45f)) {
        Text(
            text = "功能菜单",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(16.dp),
        )
        NavigationDrawerItem(
            icon = { Icon(Icons.Default.Menu, contentDescription = null) },
            label = { Text("收支管理") },
            selected = currentFullScreen == FullScreen.TRANSACTIONS,
            onClick = { onItemClick(FullScreen.TRANSACTIONS) },
        )
        NavigationDrawerItem(
            icon = { Icon(Icons.Default.Menu, contentDescription = null) },
            label = { Text("管理标签") },
            selected = currentFullScreen == FullScreen.TAG_MANAGE,
            onClick = { onItemClick(FullScreen.TAG_MANAGE) },
        )
        NavigationDrawerItem(
            icon = { Icon(Icons.Default.Menu, contentDescription = null) },
            label = { Text("API 管理") },
            selected = currentFullScreen == FullScreen.API_SETTINGS,
            onClick = { onItemClick(FullScreen.API_SETTINGS) },
        )
        NavigationDrawerItem(
            icon = { Icon(Icons.Default.Menu, contentDescription = null) },
            label = { Text("报告") },
            selected = currentFullScreen == FullScreen.REPORT,
            onClick = { onItemClick(FullScreen.REPORT) },
        )
        NavigationDrawerItem(
            icon = { Icon(Icons.Default.Menu, contentDescription = null) },
            label = { Text("管理账户") },
            selected = currentFullScreen == FullScreen.ACCOUNT_MANAGE,
            onClick = { onItemClick(FullScreen.ACCOUNT_MANAGE) },
        )
    }
}
