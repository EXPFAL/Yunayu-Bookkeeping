package com.expfal.yunayu.ui.screen.home

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import com.expfal.yunayu.ui.screen.budget.BudgetViewModel
import com.expfal.yunayu.ui.screen.budget.SemesterSetupSheet
import com.expfal.yunayu.ui.screen.quickadd.QuickAddSheet

/** 首页：预算看板卡片置顶，悬浮「快速记账」按钮唤起快捷记账弹层。 */
@Composable
fun HomeScreen(modifier: Modifier = Modifier) {
    var showQuickAdd by remember { mutableStateOf(false) }
    var showSemesterSetup by remember { mutableStateOf(false) }
    val budgetViewModel: BudgetViewModel = viewModel()
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
                semester = budgetState.semester,
                snapshot = budgetState.snapshot,
                onEdit = { showSemesterSetup = true },
                onSetup = { showSemesterSetup = true },
            )
            Spacer(modifier = Modifier.height(32.dp))
            Text(text = "Yunayu 记账", style = MaterialTheme.typography.headlineMedium)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "点右下角「+」，3 秒记下一笔",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    if (showQuickAdd) {
        QuickAddSheet(
            onDismissRequest = { showQuickAdd = false },
            onSaved = { showQuickAdd = false },
        )
    }
    if (showSemesterSetup) {
        SemesterSetupSheet(
            viewModel = budgetViewModel,
            onDismissRequest = { showSemesterSetup = false },
        )
    }
}
