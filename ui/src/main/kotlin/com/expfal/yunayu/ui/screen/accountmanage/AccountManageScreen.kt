package com.expfal.yunayu.ui.screen.accountmanage

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.expfal.yunayu.domain.model.Account
import com.expfal.yunayu.ui.util.centsToInitialBalanceText
import com.expfal.yunayu.ui.util.filterBudgetInput
import com.expfal.yunayu.ui.util.parseInitialBalanceToCents

/**
 * 「管理账户」全屏：账户列表（名称 + 交易数），行尾编辑 / 删除入口，右上角新增账户。
 *
 * 新增 / 编辑经弹窗内 [OutlinedTextField]（账户名 + 期初余额） + 内联错误，期初余额金额输入
 * 复用 [filterBudgetInput] / [parseInitialBalanceToCents] 校验；删除经「先算影响面、再二次确认」
 * 两段式；成功 / 失败事件驱动 Snackbar。子 Composable 各自独立，控制在 80 行内。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountManageScreen(
    onBack: () -> Unit,
    viewModel: AccountManageViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var showAddDialog by remember { mutableStateOf(false) }
    var addSubmitted by remember { mutableStateOf(false) }

    val handleBack = {
        viewModel.cancelDelete()
        viewModel.dismissEdit()
        viewModel.clearError()
        onBack()
    }
    BackHandler(onBack = handleBack)

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                AccountManageEvent.Added -> snackbarHostState.showSnackbar("已添加")
                AccountManageEvent.Updated -> snackbarHostState.showSnackbar("已保存")
                AccountManageEvent.Deleted -> snackbarHostState.showSnackbar("已删除")
                is AccountManageEvent.Failed -> snackbarHostState.showSnackbar(event.message)
            }
        }
    }

    LaunchedEffect(uiState.busy, uiState.errorMessage) {
        if (addSubmitted && !uiState.busy) {
            if (uiState.errorMessage == null) showAddDialog = false
            addSubmitted = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("管理账户") },
                navigationIcon = {
                    IconButton(onClick = handleBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    TextButton(
                        onClick = {
                            showAddDialog = true
                            addSubmitted = false
                            viewModel.clearError()
                        },
                    ) {
                        Text("添加")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        when {
            uiState.loading -> LoadingState(Modifier.fillMaxSize().padding(innerPadding))
            uiState.accounts.isEmpty() -> EmptyState(Modifier.fillMaxSize().padding(innerPadding))
            else -> AccountList(
                rows = uiState.accounts,
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                onEdit = viewModel::requestEdit,
                onDelete = viewModel::requestDelete,
            )
        }
    }

    if (showAddDialog) {
        AddAccountDialog(
            errorMessage = uiState.errorMessage,
            onConfirm = { name, cents ->
                addSubmitted = true
                viewModel.addAccount(name, cents)
            },
            onDismiss = {
                showAddDialog = false
                viewModel.clearError()
            },
        )
    }

    uiState.editingAccount?.let { account ->
        EditAccountDialog(
            account = account,
            errorMessage = uiState.errorMessage,
            onConfirm = { name, cents -> viewModel.updateAccount(account.id, name, cents) },
            onDismiss = { viewModel.dismissEdit() },
        )
    }

    uiState.pendingDelete?.let { target ->
        DeleteAccountDialog(
            target = target,
            onConfirm = { viewModel.confirmDelete() },
            onDismiss = { viewModel.cancelDelete() },
        )
    }
}

/** 账户列表：每行名称 + 交易数 + 编辑 / 删除入口。 */
@Composable
private fun AccountList(
    rows: List<AccountRow>,
    modifier: Modifier = Modifier,
    onEdit: (Account) -> Unit,
    onDelete: (Account) -> Unit,
) {
    LazyColumn(modifier) {
        items(rows, key = { it.account.id }) { row ->
            AccountListRow(
                row = row,
                onEdit = { onEdit(row.account) },
                onDelete = { onDelete(row.account) },
            )
        }
    }
}

/** 账户行：名称 + 交易数 + 编辑 / 删除图标。 */
@Composable
private fun AccountListRow(
    row: AccountRow,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(row.account.name, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = "${row.transactionCount} 条记录",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onEdit) {
            Icon(Icons.Default.Edit, contentDescription = "编辑")
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Default.Delete, contentDescription = "删除", tint = MaterialTheme.colorScheme.error)
        }
    }
}

/** 新增账户弹窗：账户名 + 期初余额（可留空），空名或非法金额禁用确认，错误文案内联。 */
@Composable
private fun AddAccountDialog(
    errorMessage: String?,
    onConfirm: (String, Long) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var initialBalanceText by remember { mutableStateOf("") }
    val initialBalanceCents = parseInitialBalanceToCents(initialBalanceText)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加账户") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("账户名") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = initialBalanceText,
                    onValueChange = { initialBalanceText = filterBudgetInput(it) },
                    label = { Text("期初余额（元，可留空）") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (errorMessage != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        errorMessage,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name, initialBalanceCents ?: 0L) },
                enabled = name.isNotBlank() && initialBalanceCents != null,
            ) { Text("添加") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

/** 编辑账户弹窗：账户名 + 期初余额预填当前值，空名或非法金额禁用确认，失败透出错误文案。 */
@Composable
private fun EditAccountDialog(
    account: Account,
    errorMessage: String?,
    onConfirm: (String, Long) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(account.name) }
    var initialBalanceText by remember { mutableStateOf(centsToInitialBalanceText(account.initialBalanceCents)) }
    val initialBalanceCents = parseInitialBalanceToCents(initialBalanceText)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("编辑账户") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("账户名") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = initialBalanceText,
                    onValueChange = { initialBalanceText = filterBudgetInput(it) },
                    label = { Text("期初余额（元）") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (errorMessage != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        errorMessage,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name, initialBalanceCents ?: 0L) },
                enabled = name.isNotBlank() && initialBalanceCents != null,
            ) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

/** 删除二次确认弹窗：展示受影响交易数与转账数，删除用错误色突出。 */
@Composable
private fun DeleteAccountDialog(
    target: AccountDeleteTarget,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val transferPart = if (target.affectedTransferCount > 0) {
        "，${target.affectedTransferCount} 笔转账将被删除"
    } else {
        ""
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("删除账户") },
        text = {
            Text("删除「${target.account.name}」后，${target.affectedTransactionCount} 条记录将变为未指定$transferPart，确定吗？")
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("删除", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("再想想") } },
    )
}

/** 加载态占位。 */
@Composable
private fun LoadingState(modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Text("加载中…", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** 空列表占位。 */
@Composable
private fun EmptyState(modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Text("暂无账户，点右上角「添加」新建", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
