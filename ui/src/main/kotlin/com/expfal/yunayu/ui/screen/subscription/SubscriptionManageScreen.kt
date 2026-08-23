package com.expfal.yunayu.ui.screen.subscription

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.expfal.yunayu.domain.model.SUBSCRIPTION_DUE_WINDOW_DAYS
import com.expfal.yunayu.domain.model.Subscription
import com.expfal.yunayu.domain.model.SubscriptionBillingCycle
import com.expfal.yunayu.domain.model.displayLabel
import com.expfal.yunayu.domain.model.initialBillingStartAt
import com.expfal.yunayu.domain.model.monthlyAmortizedCents
import com.expfal.yunayu.ui.util.centsToInitialBalanceText
import com.expfal.yunayu.ui.util.filterBudgetInput
import com.expfal.yunayu.ui.util.formatCents
import com.expfal.yunayu.ui.util.formatDate
import com.expfal.yunayu.ui.util.parseDateToStartOfDayMillis
import com.expfal.yunayu.ui.util.parseInitialBalanceToCents

/**
 * 「订阅开支」全屏：顶部总览（月均摊 / 折合年费），下方订阅项列表。
 *
 * 年付、季付项按 12 / 3 折算月均摊，便于与月度预算对照；支持暂停单项而不删除。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubscriptionManageScreen(
    onBack: () -> Unit,
    viewModel: SubscriptionManageViewModel = viewModel(),
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
                SubscriptionManageEvent.Saved -> snackbarHostState.showSnackbar("已保存")
                SubscriptionManageEvent.Deleted -> snackbarHostState.showSnackbar("已删除")
                is SubscriptionManageEvent.Posted -> snackbarHostState.showSnackbar("「${event.name}」已记入最近记录")
                is SubscriptionManageEvent.PostedAll -> snackbarHostState.showSnackbar("已记 ${event.count} 笔订阅")
                is SubscriptionManageEvent.Failed -> snackbarHostState.showSnackbar(event.message)
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
                title = { Text("订阅开支") },
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
            else -> SubscriptionContent(
                uiState = uiState,
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                onEdit = viewModel::requestEdit,
                onDelete = viewModel::requestDelete,
                onPostCharge = viewModel::postCharge,
                onPostAllDue = viewModel::postAllDue,
            )
        }
    }

    if (showAddDialog) {
        SubscriptionFormDialog(
            title = "添加订阅",
            confirmLabel = "添加",
            errorMessage = uiState.errorMessage,
            onConfirm = { name, cents, cycle, note, active, billingStartAt, lastPostedDueAt ->
                addSubmitted = true
                viewModel.addSubscription(name, cents, cycle, note, active, billingStartAt, lastPostedDueAt)
            },
            onDismiss = {
                showAddDialog = false
                viewModel.clearError()
            },
        )
    }

    uiState.editingSubscription?.let { subscription ->
        SubscriptionFormDialog(
            title = "编辑订阅",
            confirmLabel = "保存",
            initial = subscription,
            errorMessage = uiState.errorMessage,
            onConfirm = { name, cents, cycle, note, active, billingStartAt, lastPostedDueAt ->
                viewModel.updateSubscription(
                    id = subscription.id,
                    name = name,
                    amountCents = cents,
                    billingCycle = cycle,
                    note = note,
                    isActive = active,
                    billingStartAt = billingStartAt,
                    lastPostedDueAt = lastPostedDueAt,
                    lastPostedAt = subscription.lastPostedAt,
                    createdAt = subscription.createdAt,
                )
            },
            onDismiss = { viewModel.dismissEdit() },
        )
    }

    uiState.pendingDelete?.let { subscription ->
        AlertDialog(
            onDismissRequest = { viewModel.cancelDelete() },
            title = { Text("删除订阅") },
            text = { Text("删除「${subscription.name}」？此操作不可恢复") },
            confirmButton = {
                TextButton(onClick = { viewModel.confirmDelete() }) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { viewModel.cancelDelete() }) { Text("取消") } },
        )
    }
}

@Composable
private fun SubscriptionContent(
    uiState: SubscriptionManageUiState,
    modifier: Modifier = Modifier,
    onEdit: (Subscription) -> Unit,
    onDelete: (Subscription) -> Unit,
    onPostCharge: (Long) -> Unit,
    onPostAllDue: () -> Unit,
) {
    LazyColumn(
        modifier = modifier.padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(key = "summary") {
            SubscriptionSummaryCard(
                totalMonthlyCents = uiState.totalMonthlyCents,
                activeCount = uiState.activeCount,
                dueCount = uiState.dueCount,
                onPostAllDue = onPostAllDue,
                busy = uiState.busy,
            )
            Spacer(Modifier.height(8.dp))
        }
        if (uiState.subscriptions.isEmpty()) {
            item(key = "empty") {
                Text(
                    "暂无订阅项，点右上角添加",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 24.dp),
                )
            }
        } else {
            items(uiState.subscriptions, key = { it.id }) { subscription ->
                SubscriptionRow(
                    subscription = subscription,
                    onEdit = { onEdit(subscription) },
                    onDelete = { onDelete(subscription) },
                    onPostCharge = { onPostCharge(subscription.id) },
                    busy = uiState.busy,
                )
            }
        }
    }
}

@Composable
private fun SubscriptionSummaryCard(
    totalMonthlyCents: Long,
    activeCount: Int,
    dueCount: Int,
    onPostAllDue: () -> Unit,
    busy: Boolean,
) {
    val yearlyCents = totalMonthlyCents * 12
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("订阅总览", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text(
                text = "月均摊 ${formatCents(totalMonthlyCents)}",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = "折合年费 ${formatCents(yearlyCents)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = when {
                    activeCount == 0 -> "暂无生效中的订阅"
                    dueCount > 0 -> "共 $activeCount 项生效 · $dueCount 项待记一笔"
                    else -> "共 $activeCount 项生效中"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (dueCount > 0) {
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = onPostAllDue, enabled = !busy) {
                    Text("全部记一笔（$dueCount）")
                }
            }
        }
    }
}

@Composable
private fun SubscriptionRow(
    subscription: Subscription,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onPostCharge: () -> Unit,
    busy: Boolean,
) {
    val alphaColor = if (subscription.isActive) {
        MaterialTheme.colorScheme.onSurface
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    val dueWithinWindow = subscription.isActive && subscription.isDueWithin(SUBSCRIPTION_DUE_WINDOW_DAYS)
    val dueLabel = when {
        !subscription.isActive -> null
        subscription.isOverdue() -> "已到期"
        subscription.daysUntilDue() == 0L -> "今天扣费"
        subscription.daysUntilDue() > 0L -> "${subscription.daysUntilDue()} 天后"
        else -> null
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(subscription.name, style = MaterialTheme.typography.bodyLarge, color = alphaColor)
                if (!subscription.isActive) {
                    Text(
                        " · 已暂停",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Text(
                text = "${formatCents(subscription.amountCents)} / ${subscription.billingCycle.displayLabel()}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = buildString {
                    append("起始 ${formatDate(subscription.billingStartAt)}")
                    append(" · 本期扣费 ${formatDate(subscription.nextChargeDueAt())}")
                    if (dueLabel != null) append(" · $dueLabel")
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (subscription.isOverdue()) {
                    MaterialTheme.colorScheme.error
                } else if (dueWithinWindow) {
                    MaterialTheme.colorScheme.tertiary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
            subscription.note?.let { note ->
                Text(
                    note,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = "月均 ${formatCents(subscription.monthlyAmortizedCents)}",
                style = MaterialTheme.typography.bodyMedium,
                color = if (subscription.isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (dueWithinWindow) {
                TextButton(onClick = onPostCharge, enabled = !busy) {
                    Text("记一笔")
                }
            }
        }
        IconButton(onClick = onEdit) {
            Icon(Icons.Default.Edit, contentDescription = "编辑")
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Default.Delete, contentDescription = "删除", tint = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun SubscriptionFormDialog(
    title: String,
    confirmLabel: String,
    errorMessage: String?,
    onConfirm: (String, Long, SubscriptionBillingCycle, String?, Boolean, Long, Long?) -> Unit,
    onDismiss: () -> Unit,
    initial: Subscription? = null,
) {
    var name by remember(initial) { mutableStateOf(initial?.name.orEmpty()) }
    var amountText by remember(initial) {
        mutableStateOf(initial?.let { centsToInitialBalanceText(it.amountCents) }.orEmpty())
    }
    var cycle by remember(initial) { mutableStateOf(initial?.billingCycle ?: SubscriptionBillingCycle.YEARLY) }
    var note by remember(initial) { mutableStateOf(initial?.note.orEmpty()) }
    var isActive by remember(initial) { mutableStateOf(initial?.isActive ?: true) }
    var billingStartText by remember(initial) {
        mutableStateOf(formatDate(initial?.billingStartAt ?: initialBillingStartAt()))
    }
    var lastPostedDueText by remember(initial) {
        mutableStateOf(initial?.lastPostedDueAt?.let { formatDate(it) }.orEmpty())
    }
    val amountCents = parseInitialBalanceToCents(amountText)
    val billingStartAt = parseDateToStartOfDayMillis(billingStartText)
    val lastPostedDueAt = lastPostedDueText.trim().takeIf { it.isNotEmpty() }?.let { parseDateToStartOfDayMillis(it) }
    val lastPostedDueInvalid = lastPostedDueText.trim().isNotEmpty() && lastPostedDueAt == null
    val lastPostedBeforeStart = billingStartAt != null && lastPostedDueAt != null && lastPostedDueAt < billingStartAt

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = filterBudgetInput(it) },
                    label = { Text("单次扣费（元）") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                Text("计费周期", style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SubscriptionBillingCycle.entries.forEach { option ->
                        FilterChip(
                            selected = cycle == option,
                            onClick = { cycle = option },
                            label = { Text(option.displayLabel()) },
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("备注（可选）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = billingStartText,
                    onValueChange = { billingStartText = it },
                    label = { Text("起始日（yyyy-MM-dd）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = lastPostedDueText,
                    onValueChange = { lastPostedDueText = it },
                    label = { Text("已记至（可选）") },
                    placeholder = { Text("账本已记过的最后一期扣费日") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    isError = lastPostedDueInvalid || lastPostedBeforeStart,
                    supportingText = {
                        when {
                            lastPostedDueInvalid -> Text("日期格式须为 yyyy-MM-dd")
                            lastPostedBeforeStart -> Text("不能早于起始日")
                            else -> Text("已在收支里记过的填这里，避免重复记一笔")
                        }
                    },
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("计入总览")
                    Switch(checked = isActive, onCheckedChange = { isActive = it })
                }
                if (amountCents != null && amountCents > 0) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "月均摊约 ${formatCents(cycle.monthlyAmortizedCents(amountCents))}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (errorMessage != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(errorMessage, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (amountCents != null && billingStartAt != null && !lastPostedDueInvalid && !lastPostedBeforeStart) {
                        onConfirm(name, amountCents, cycle, note, isActive, billingStartAt, lastPostedDueAt)
                    }
                },
                enabled = name.isNotBlank() && amountCents != null && amountCents > 0 &&
                    billingStartAt != null && !lastPostedDueInvalid && !lastPostedBeforeStart,
            ) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun LoadingState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("加载中…", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
