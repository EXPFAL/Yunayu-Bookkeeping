package com.expfal.yunayu.ui.screen.transactionmanage

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.expfal.yunayu.domain.model.Account
import com.expfal.yunayu.ui.component.DateTimePickerFlow
import com.expfal.yunayu.ui.screen.quickadd.NumberPad
import com.expfal.yunayu.ui.util.formatCents
import com.expfal.yunayu.ui.util.formatTime
import com.expfal.yunayu.ui.util.parseAmountToCents
import com.expfal.yunayu.ui.util.vibrateSuccess

/** 「转账编辑」全屏：金额 / 转出转入 / 备注 / 发生时间。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditTransferScreen(
    transferId: Long,
    onBack: () -> Unit,
    onSaved: () -> Unit,
    viewModel: EditTransferViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showDateTimePicker by remember { mutableStateOf(false) }

    LaunchedEffect(transferId) {
        viewModel.open(transferId)
    }

    LaunchedEffect(viewModel, context) {
        viewModel.events.collect { event ->
            when (event) {
                EditTransferEvent.Saved -> {
                    context.vibrateSuccess()
                    onSaved()
                }
                EditTransferEvent.SaveFailed -> Unit
            }
        }
    }

    BackHandler {
        if (!uiState.saving) onBack()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("编辑转账") },
                navigationIcon = {
                    IconButton(onClick = { if (!uiState.saving) onBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            when {
                uiState.loading -> {
                    Text(
                        "加载中…",
                        modifier = Modifier.padding(24.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                uiState.loadFailed -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text("加载失败，请重试")
                    }
                }
                else -> {
                    Column(modifier = Modifier.padding(horizontal = 24.dp)) {
                        Text(
                            text = "¥ " + formatCents(parseAmountToCents(uiState.amountText) ?: 0L),
                            style = MaterialTheme.typography.displayLarge,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("转出", style = MaterialTheme.typography.labelMedium)
                        AccountPickRow(
                            accounts = uiState.accounts,
                            selectedId = uiState.fromAccountId,
                            onSelect = viewModel::onSelectFrom,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("转入", style = MaterialTheme.typography.labelMedium)
                        AccountPickRow(
                            accounts = uiState.accounts,
                            selectedId = uiState.toAccountId,
                            onSelect = viewModel::onSelectTo,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        EditNoteField(note = uiState.note, onNoteChange = viewModel::onNoteChange)
                        Text(
                            text = "时间  " + formatTime(uiState.occurredAt),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = !uiState.saving) { showDateTimePicker = true }
                                .padding(vertical = 8.dp),
                        )
                        uiState.validationError?.let {
                            Text(it, color = MaterialTheme.colorScheme.error)
                        }
                    }
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp)
                            .padding(bottom = 28.dp),
                    ) {
                        NumberPad(onDigit = viewModel::onDigit, onDelete = viewModel::onDelete)
                        Spacer(modifier = Modifier.height(16.dp))
                        EditActionsRow(
                            saving = uiState.saving,
                            onSave = viewModel::onSave,
                            onCancel = onBack,
                        )
                        if (uiState.saveFailed) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "刚才没保存上，再试一次",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }
        }
    }

    if (showDateTimePicker) {
        DateTimePickerFlow(
            initialMillis = uiState.occurredAt,
            onConfirm = {
                viewModel.onOccurredAtChange(it)
                showDateTimePicker = false
            },
            onDismiss = { showDateTimePicker = false },
        )
    }
}

@Composable
private fun AccountPickRow(
    accounts: List<Account>,
    selectedId: Long?,
    onSelect: (Long) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        accounts.forEach { account ->
            FilterChip(
                selected = selectedId == account.id,
                onClick = { onSelect(account.id) },
                label = { Text(account.name) },
            )
        }
    }
}
