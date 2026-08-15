package com.expfal.yunayu.ui.screen.home

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.expfal.yunayu.ui.screen.apiconfig.ApiSettingsScreen
import com.expfal.yunayu.ui.screen.budget.BudgetCard
import com.expfal.yunayu.ui.screen.budget.MonthlyBudgetSheet
import com.expfal.yunayu.ui.screen.budget.MonthlyBudgetViewModel
import com.expfal.yunayu.ui.screen.quickadd.QuickAddSheet
import com.expfal.yunayu.ui.screen.report.ReportScreen
import com.expfal.yunayu.ui.screen.tagmanage.TagManageScreen
import com.expfal.yunayu.ui.screen.transactionmanage.TransactionManageScreen

/** 首页全屏子界面：无 / 标签管理 / API 管理 / 分析报告 / 收支管理，五态互斥。 */
private enum class FullScreen { NONE, TAG_MANAGE, API_SETTINGS, REPORT, TRANSACTIONS }

/** 首页：月度预算看板卡片置顶，下方最近记录列表，悬浮「快速记账」按钮唤起快捷记账弹层。 */
@Composable
fun HomeScreen(modifier: Modifier = Modifier) {
    var showQuickAdd by remember { mutableStateOf(false) }
    var showBudgetSetup by remember { mutableStateOf(false) }
    var fullScreen by remember { mutableStateOf(FullScreen.NONE) }
    val budgetViewModel: MonthlyBudgetViewModel = viewModel()
    val budgetState by budgetViewModel.uiState.collectAsStateWithLifecycle()
    val homeViewModel: HomeViewModel = viewModel()
    val homeState by homeViewModel.uiState.collectAsStateWithLifecycle()

    when (fullScreen) {
        FullScreen.TAG_MANAGE -> TagManageScreen(onBack = { fullScreen = FullScreen.NONE })
        FullScreen.API_SETTINGS -> ApiSettingsScreen(onBack = { fullScreen = FullScreen.NONE })
        FullScreen.REPORT -> ReportScreen(onBack = { fullScreen = FullScreen.NONE })
        FullScreen.TRANSACTIONS -> TransactionManageScreen(onBack = { fullScreen = FullScreen.NONE })
        FullScreen.NONE -> {
            Scaffold(
                modifier = modifier,
                floatingActionButton = {
                    FloatingActionButton(onClick = { showQuickAdd = true }) {
                        Icon(imageVector = Icons.Default.Add, contentDescription = "快速记账")
                    }
                },
            ) { innerPadding ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .padding(24.dp),
                ) {
                    BudgetCard(
                        loading = budgetState.loading,
                        budgetCents = budgetState.budgetCents,
                        snapshot = budgetState.snapshot,
                        onEdit = { showBudgetSetup = true },
                        onSetup = { showBudgetSetup = true },
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    HeldFundsCard(heldCents = homeState.heldCents)
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                    ) {
                        TextButton(onClick = { fullScreen = FullScreen.TRANSACTIONS }) {
                            Text("收支管理")
                        }
                        TextButton(onClick = { fullScreen = FullScreen.TAG_MANAGE }) {
                            Text("管理标签")
                        }
                        TextButton(onClick = { fullScreen = FullScreen.API_SETTINGS }) {
                            Text("API 管理")
                        }
                        TextButton(onClick = { fullScreen = FullScreen.REPORT }) {
                            Text("报告")
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    if (!homeState.loading && homeState.recent.isEmpty()) {
                        FirstRunHint()
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                    RecentTransactionsCard(
                        uiState = homeState,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }

    if (showQuickAdd) {
        QuickAddSheet(
            onDismissRequest = { showQuickAdd = false },
            onSaved = { showQuickAdd = false },
        )
    }
    if (showBudgetSetup) {
        MonthlyBudgetSheet(
            viewModel = budgetViewModel,
            onDismissRequest = { showBudgetSetup = false },
        )
    }
}

/** 首次引导横幅：无任何交易记录时提示「点右下角 +，3 秒记一笔」，记下第一笔后随列表非空自动消失。 */
@Composable
private fun FirstRunHint(modifier: Modifier = Modifier) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = modifier.fillMaxWidth(),
    ) {
        Text(
            "点右下角 +，3 秒记一笔",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        )
    }
}
