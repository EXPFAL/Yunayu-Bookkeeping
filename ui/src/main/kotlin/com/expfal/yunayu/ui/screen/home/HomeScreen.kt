package com.expfal.yunayu.ui.screen.home

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.expfal.yunayu.ui.screen.budget.BudgetCard
import com.expfal.yunayu.ui.screen.budget.MonthlyBudgetSheet
import com.expfal.yunayu.ui.screen.budget.MonthlyBudgetViewModel
import com.expfal.yunayu.ui.screen.quickadd.QuickAddSheet

/** 首页：月度预算看板卡片置顶，悬浮「快速记账」按钮唤起快捷记账弹层。 */
@Composable
fun HomeScreen(modifier: Modifier = Modifier) {
    var showQuickAdd by remember { mutableStateOf(false) }
    var showBudgetSetup by remember { mutableStateOf(false) }
    val budgetViewModel: MonthlyBudgetViewModel = viewModel()
    val budgetState by budgetViewModel.uiState.collectAsStateWithLifecycle()

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
