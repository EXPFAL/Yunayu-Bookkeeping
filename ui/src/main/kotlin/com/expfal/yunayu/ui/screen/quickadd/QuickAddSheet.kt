package com.expfal.yunayu.ui.screen.quickadd

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.expfal.yunayu.domain.model.Account
import com.expfal.yunayu.domain.model.Tag
import com.expfal.yunayu.domain.model.TransactionType
import com.expfal.yunayu.ui.component.TagTreeList
import com.expfal.yunayu.ui.util.formatCents
import com.expfal.yunayu.ui.util.tagDisplayName
import com.expfal.yunayu.ui.util.vibrateSuccess

/**
 * 「3秒极速记账」底部弹层：金额大字 + 最近分类预选 + 自绘数字键盘 + 保存。
 *
 * 监听 ViewModel 的 [QuickAddEvent.Saved]，成功后震动并回调 [onSaved] 关闭弹层；
 * 大额（> ¥100）时弹出温和的「必要支出」确认对话框。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickAddSheet(
    onDismissRequest: () -> Unit,
    onSaved: () -> Unit,
    viewModel: QuickAddViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showTagPicker by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.resetForOpen()
        viewModel.refreshSuggestedTags()
    }

    LaunchedEffect(viewModel, context) {
        viewModel.events.collect { event ->
            when (event) {
                QuickAddEvent.Saved -> {
                    context.vibrateSuccess()
                    onSaved()
                }
                QuickAddEvent.SaveFailed -> Unit // 提示文案由 uiState.saveFailed 驱动
            }
        }
    }

    if (uiState.confirmRequested) {
        NecessaryExpenseDialog(
            isIncome = if (uiState.nlMode) {
                uiState.nlDraft?.type == TransactionType.INCOME
            } else {
                uiState.transactionType == TransactionType.INCOME
            },
            onConfirm = viewModel::onConfirmNecessary,
            onDismiss = viewModel::onDismissConfirm,
        )
    }

    ModalBottomSheet(
        onDismissRequest = {
            if (!uiState.saving && !uiState.nlParsing) onDismissRequest()
        },
    ) {
        QuickAddForm(
            uiState = uiState,
            viewModel = viewModel,
            onShowTagPicker = { showTagPicker = true },
        )
    }

    if (showTagPicker) {
        TagPickerSheet(
            allTagsByRoot = uiState.allTagsByRoot,
            selectedTagId = uiState.selectedTagId,
            onSelect = { tagId ->
                viewModel.onSelectTag(tagId)
                showTagPicker = false
            },
            onDismiss = { showTagPicker = false },
        )
    }
}

/** 快捷记账表单主体：模式切换 + 收支/转账表单 + 输入/解析区 + 反馈文案。 */
@Composable
private fun QuickAddForm(
    uiState: QuickAddUiState,
    viewModel: QuickAddViewModel,
    onShowTagPicker: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .padding(bottom = 28.dp),
    ) {
        NlModeToggle(
            nlMode = uiState.nlMode,
            onModeChange = viewModel::setNlMode,
        )
        Spacer(modifier = Modifier.height(16.dp))
        ExpenseFormHeader(uiState = uiState, viewModel = viewModel)
        if (!uiState.nlMode && uiState.transferMode) {
            TransferFormBranch(
                accounts = uiState.accounts,
                fromAccountId = uiState.fromAccountId,
                toAccountId = uiState.toAccountId,
                note = uiState.transferNote,
                onSelectFrom = viewModel::onSelectFromAccount,
                onSelectTo = viewModel::onSelectToAccount,
                onNoteChange = viewModel::onTransferNoteChange,
            )
        } else {
            ExpenseFormBranch(
                nlMode = uiState.nlMode,
                suggestedTags = uiState.suggestedTags,
                selectedTagId = uiState.selectedTagId,
                rootNameById = uiState.rootNameById,
                nlDraftType = uiState.nlDraft?.type,
                transactionType = uiState.transactionType,
                accounts = uiState.accounts,
                selectedAccountId = uiState.selectedAccountId,
                onSelectTag = viewModel::onSelectTag,
                onLoadAllTags = viewModel::loadAllTags,
                onSelectAccount = viewModel::onSelectAccount,
                onShowTagPicker = onShowTagPicker,
            )
        }
        if (uiState.nlMode) {
            NlParseSection(
                inputText = uiState.nlInputText,
                parsing = uiState.nlParsing,
                saving = uiState.saving,
                draft = uiState.nlDraft,
                failure = uiState.nlFailure,
                nlTagId = uiState.nlTagId,
                suggestedTags = uiState.suggestedTags,
                rootNameById = uiState.rootNameById,
                allTagsByRoot = uiState.allTagsByRoot,
                onInputChange = viewModel::onNlInputChange,
                onParse = viewModel::onParseNl,
                onSave = viewModel::onSaveNl,
            )
        } else {
            NumberInputSection(uiState = uiState, viewModel = viewModel)
        }
        FeedbackSection(uiState = uiState)
    }
}

/** 数字模式收支/转账共用的金额头部：方向切换 + 金额大字。 */
@Composable
private fun ExpenseFormHeader(
    uiState: QuickAddUiState,
    viewModel: QuickAddViewModel,
) {
    if (uiState.nlMode) return
    TypeToggle(
        transactionType = uiState.transactionType,
        transferMode = uiState.transferMode,
        onTypeChange = viewModel::setType,
        onTransfer = viewModel::setTransferMode,
    )
    Spacer(modifier = Modifier.height(16.dp))
    Text(
        text = "¥ " + formatCents(QuickAddViewModel.parseAmountToCents(uiState.amountText) ?: 0L),
        style = MaterialTheme.typography.displayLarge,
        color = MaterialTheme.colorScheme.onSurface,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(modifier = Modifier.height(16.dp))
}

/** 转账表单分支块：转出/转入账户 + 备注。 */
@Composable
private fun TransferFormBranch(
    accounts: List<Account>,
    fromAccountId: Long?,
    toAccountId: Long?,
    note: String,
    onSelectFrom: (Long) -> Unit,
    onSelectTo: (Long) -> Unit,
    onNoteChange: (String) -> Unit,
) {
    TransferAccountForm(
        accounts = accounts,
        fromAccountId = fromAccountId,
        toAccountId = toAccountId,
        note = note,
        onSelectFrom = onSelectFrom,
        onSelectTo = onSelectTo,
        onNoteChange = onNoteChange,
    )
    Spacer(modifier = Modifier.height(20.dp))
}

/** 收支表单分支块：最近分类 chips + 账户选择（NL 模式复用账户选择）。 */
@Composable
private fun ExpenseFormBranch(
    nlMode: Boolean,
    suggestedTags: List<Tag>,
    selectedTagId: Long?,
    rootNameById: Map<Long, String>,
    nlDraftType: TransactionType?,
    transactionType: TransactionType,
    accounts: List<Account>,
    selectedAccountId: Long?,
    onSelectTag: (Long) -> Unit,
    onLoadAllTags: (TransactionType) -> Unit,
    onSelectAccount: (Long?) -> Unit,
    onShowTagPicker: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        suggestedTags.forEach { tag ->
            FilterChip(
                selected = selectedTagId == tag.id,
                onClick = { onSelectTag(tag.id) },
                label = { Text(tagDisplayName(tag, rootNameById)) },
            )
        }
        AssistChip(
            onClick = {
                val type = if (nlMode) {
                    nlDraftType ?: TransactionType.EXPENSE
                } else {
                    transactionType
                }
                onLoadAllTags(type)
                onShowTagPicker()
            },
            label = { Text("更多") },
        )
    }
    AccountChipsRow(
        accounts = accounts,
        selectedAccountId = selectedAccountId,
        onSelect = onSelectAccount,
        modifier = Modifier.padding(top = 12.dp),
    )
    Spacer(modifier = Modifier.height(20.dp))
}

/** 数字模式保存区：数字键盘 + 保存按钮。 */
@Composable
private fun NumberInputSection(
    uiState: QuickAddUiState,
    viewModel: QuickAddViewModel,
) {
    if (!uiState.transferMode) {
        OutlinedTextField(
            value = uiState.manualNote,
            onValueChange = viewModel::onManualNoteChange,
            placeholder = { Text("备注（可选）") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(12.dp))
    }
    NumberPad(
        onDigit = viewModel::onDigit,
        onDelete = viewModel::onDelete,
    )
    Spacer(modifier = Modifier.height(20.dp))
    Button(
        onClick = viewModel::onSave,
        enabled = !uiState.saving &&
            QuickAddViewModel.parseAmountToCents(uiState.amountText) != null &&
            (!uiState.transferMode || (uiState.fromAccountId != null && uiState.toAccountId != null)),
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp),
    ) {
        Text(
            text = when {
                uiState.saving -> if (uiState.transferMode) "转账中…" else "记一笔中…"
                uiState.transferMode -> "转账"
                else -> "记一笔"
            },
        )
    }
}

/** 转账校验错误与保存失败提示区。 */
@Composable
private fun FeedbackSection(uiState: QuickAddUiState) {
    uiState.transferError?.let { transferError ->
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = transferError,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
    if (uiState.saveFailed) {
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "刚才没记上，再试一次",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** 「更多分类」选择层：按根分组折叠展示子标签（仅子类可选，父类仅作分组头；筛选宿主除外），点选即选中并关闭。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TagPickerSheet(
    allTagsByRoot: Map<Tag, List<Tag>>,
    selectedTagId: Long?,
    onSelect: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        TagPickerTitle(title = "选择标签")
        Spacer(modifier = Modifier.height(8.dp))
        if (allTagsByRoot.isEmpty()) {
            Text(
                text = "暂无可用标签",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
            )
        } else {
            TagTreeList(
                allTagsByRoot = allTagsByRoot,
                selectedIds = setOfNotNull(selectedTagId),
                onToggleSelect = onSelect,
                modifier = Modifier.padding(bottom = 28.dp),
            )
        }
    }
}

/** 选择层标题行。 */
@Composable
private fun TagPickerTitle(
    title: String,
) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
    )
}

/**
 * 账户选择横向 chips：首位固定「未指定」+ 各账户，单选互斥；账户列表为空时整行不渲染。
 * 数字与 NL 两模式共享，选中的 [selectedAccountId] 在落库时透传为交易账户外键。
 */
@Composable
private fun AccountChipsRow(
    accounts: List<Account>,
    selectedAccountId: Long?,
    onSelect: (Long?) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (accounts.isEmpty()) return
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterChip(
            selected = selectedAccountId == null,
            onClick = { onSelect(null) },
            label = { Text("未指定") },
        )
        accounts.forEach { account ->
            FilterChip(
                selected = selectedAccountId == account.id,
                onClick = { onSelect(account.id) },
                label = { Text(account.name) },
            )
        }
    }
}

/** 数字模式三段式切换控件：支出 / 收入 / 转账；仅数字模式展示，样式与 [NlModeToggle] 一致。 */
@Composable
private fun TypeToggle(
    transactionType: TransactionType,
    transferMode: Boolean,
    onTypeChange: (TransactionType) -> Unit,
    onTransfer: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterChip(
            selected = !transferMode && transactionType == TransactionType.EXPENSE,
            onClick = { onTypeChange(TransactionType.EXPENSE) },
            label = { Text("支出") },
        )
        FilterChip(
            selected = !transferMode && transactionType == TransactionType.INCOME,
            onClick = { onTypeChange(TransactionType.INCOME) },
            label = { Text("收入") },
        )
        FilterChip(
            selected = transferMode,
            onClick = onTransfer,
            label = { Text("转账") },
        )
    }
}

/**
 * 转账模式表单：转出账户 + 转入账户（全量账户 chips 单选，二者必选且不同）+ 备注输入。
 * 无标签选择与推荐 chips（转账与标签/收支口径隔离）；账户列表为空时仅提示，不渲染 chips。
 */
@Composable
private fun TransferAccountForm(
    accounts: List<Account>,
    fromAccountId: Long?,
    toAccountId: Long?,
    note: String,
    onSelectFrom: (Long) -> Unit,
    onSelectTo: (Long) -> Unit,
    onNoteChange: (String) -> Unit,
) {
    if (accounts.isEmpty()) {
        Text(
            text = "暂无可转账账户，请先添加账户",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    Column(Modifier.fillMaxWidth()) {
        Text(
            text = "转出账户",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TransferAccountChipsRow(accounts, fromAccountId, onSelectFrom)
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "转入账户",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TransferAccountChipsRow(accounts, toAccountId, onSelectTo)
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedTextField(
            value = note,
            onValueChange = onNoteChange,
            placeholder = { Text("备注（可选）") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** 转账账户 chips 行：无「未指定」选项，全量账户单选。 */
@Composable
private fun TransferAccountChipsRow(
    accounts: List<Account>,
    selectedAccountId: Long?,
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
                selected = selectedAccountId == account.id,
                onClick = { onSelect(account.id) },
                label = { Text(account.name) },
            )
        }
    }
}

/** 大额交易的温和二次确认，措辞克制、不说教；收入侧不出现「必要支出」表述。 */
@Composable
private fun NecessaryExpenseDialog(
    isIncome: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isIncome) "确认记一笔收入？" else "这笔属于必要支出吗？") },
        text = { Text(if (isIncome) "收入会计入持有资金，不计入本月支出。" else "只是帮你多确认一下。") },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("记一笔") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("再想想") }
        },
    )
}
