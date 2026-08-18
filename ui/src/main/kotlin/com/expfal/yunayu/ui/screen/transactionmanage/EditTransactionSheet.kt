package com.expfal.yunayu.ui.screen.transactionmanage

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
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
import com.expfal.yunayu.ui.screen.quickadd.NumberPad
import com.expfal.yunayu.ui.util.formatCents
import com.expfal.yunayu.ui.util.vibrateSuccess

/**
 * 「交易编辑」底部弹层：金额大数字键盘 + 收支类型切换 + 备注 + 标签 + 账户 + 保存 / 取消。
 *
 * 监听 ViewModel 的 [EditTransactionEvent.Saved]，成功后震动并回调 [onSaved] 关闭弹层；
 * 取消走 [onDismissRequest]，不产生任何写入。编辑范围不含发生时间（保持原值）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditTransactionSheet(
    transactionId: Long,
    onDismissRequest: () -> Unit,
    onSaved: () -> Unit,
    viewModel: EditTransactionViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showTagPicker by remember { mutableStateOf(false) }

    LaunchedEffect(transactionId) {
        viewModel.open(transactionId)
    }

    LaunchedEffect(viewModel, context) {
        viewModel.events.collect { event ->
            when (event) {
                EditTransactionEvent.Saved -> {
                    context.vibrateSuccess()
                    onSaved()
                }
                EditTransactionEvent.SaveFailed -> Unit // 提示文案由 uiState.saveFailed 驱动
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = { if (!uiState.saving) onDismissRequest() },
        windowInsets = WindowInsets(0),
    ) {
        Column(Modifier.imePadding()) {
            when {
                uiState.loading -> EditPlaceholder("加载中…")
                uiState.loadFailed -> EditPlaceholder("加载失败，请重试")
                else -> EditContent(
                    uiState = uiState,
                    onTypeChange = viewModel::setType,
                    onDigit = viewModel::onDigit,
                    onDelete = viewModel::onDelete,
                    onNoteChange = viewModel::onNoteChange,
                    onSelectTag = viewModel::onSelectTag,
                    onOpenTagPicker = { showTagPicker = true },
                    onSelectAccount = viewModel::onSelectAccount,
                    onSave = viewModel::onSave,
                    onCancel = onDismissRequest,
                )
            }
        }
    }

    if (showTagPicker) {
        EditTagPickerSheet(
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

/** 编辑弹层主体：金额 + 类型 + 备注 + 标签 / 账户 chips + 数字键盘 + 保存 / 取消。 */
@Composable
private fun EditContent(
    uiState: EditTransactionUiState,
    onTypeChange: (TransactionType) -> Unit,
    onDigit: (Char) -> Unit,
    onDelete: () -> Unit,
    onNoteChange: (String) -> Unit,
    onSelectTag: (Long) -> Unit,
    onOpenTagPicker: () -> Unit,
    onSelectAccount: (Long?) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .padding(bottom = 28.dp),
    ) {
        Text(
            text = "编辑记录",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(16.dp))
        EditTypeToggle(transactionType = uiState.transactionType, onTypeChange = onTypeChange)
        Spacer(Modifier.height(16.dp))
        Text(
            text = "¥ " + formatCents(EditTransactionViewModel.parseAmountToCents(uiState.amountText) ?: 0L),
            style = MaterialTheme.typography.displayLarge,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(16.dp))
        EditNoteField(note = uiState.note, onNoteChange = onNoteChange)
        Spacer(Modifier.height(12.dp))
        EditTagChipsRow(
            selectedTagId = uiState.selectedTagId,
            selectedTagName = uiState.selectedTagName,
            onSelectTag = onSelectTag,
            onOpenTagPicker = onOpenTagPicker,
        )
        EditAccountChipsRow(
            accounts = uiState.accounts,
            selectedAccountId = uiState.selectedAccountId,
            onSelect = onSelectAccount,
            modifier = Modifier.padding(top = 12.dp),
        )
        Spacer(Modifier.height(16.dp))
        NumberPad(onDigit = onDigit, onDelete = onDelete)
        Spacer(Modifier.height(16.dp))
        EditActionsRow(saving = uiState.saving, onSave = onSave, onCancel = onCancel)
        if (uiState.saveFailed) {
            Spacer(Modifier.height(12.dp))
            Text(
                text = "刚才没保存上，再试一次",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** 收/支方向切换控件，样式与快捷记账的 [com.expfal.yunayu.ui.screen.quickadd.QuickAddScreen] 对齐。 */
@Composable
private fun EditTypeToggle(
    transactionType: TransactionType,
    onTypeChange: (TransactionType) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(
            selected = transactionType == TransactionType.EXPENSE,
            onClick = { onTypeChange(TransactionType.EXPENSE) },
            label = { Text("支出") },
        )
        FilterChip(
            selected = transactionType == TransactionType.INCOME,
            onClick = { onTypeChange(TransactionType.INCOME) },
            label = { Text("收入") },
        )
    }
}

/** 备注输入框。 */
@Composable
private fun EditNoteField(
    note: String,
    onNoteChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = note,
        onValueChange = onNoteChange,
        placeholder = { Text("备注（可选）") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
}

/** 标签选择行：已选标签以选中态 chip 展示（再次点击取消），「更多」展开标签树选择层。 */
@Composable
private fun EditTagChipsRow(
    selectedTagId: Long?,
    selectedTagName: String?,
    onSelectTag: (Long) -> Unit,
    onOpenTagPicker: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (selectedTagId != null) {
            FilterChip(
                selected = true,
                onClick = { onSelectTag(selectedTagId) },
                label = { Text(selectedTagName ?: "已选标签") },
            )
        }
        AssistChip(
            onClick = onOpenTagPicker,
            label = { Text(if (selectedTagId == null) "选择标签" else "更多") },
        )
    }
}

/** 账户选择横向 chips：首位固定「未指定」+ 各账户，单选互斥；账户列表为空时整行不渲染。 */
@Composable
private fun EditAccountChipsRow(
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

/** 保存 / 取消操作行。 */
@Composable
private fun EditActionsRow(
    saving: Boolean,
    onSave: () -> Unit,
    onCancel: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        OutlinedButton(
            onClick = onCancel,
            enabled = !saving,
            modifier = Modifier
                .weight(1f)
                .height(52.dp),
        ) {
            Text("取消")
        }
        Button(
            onClick = onSave,
            enabled = !saving,
            modifier = Modifier
                .weight(1f)
                .height(52.dp),
        ) {
            Text(if (saving) "保存中…" else "保存")
        }
    }
}

/** 标签选择层：按根分组折叠展示子标签（仅子类可选，父类仅作分组头），点选即选中并关闭。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditTagPickerSheet(
    allTagsByRoot: Map<Tag, List<Tag>>,
    selectedTagId: Long?,
    onSelect: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Text(
            text = "选择标签",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 24.dp),
        )
        Spacer(Modifier.height(8.dp))
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

/** 加载 / 失败占位文案。 */
@Composable
private fun EditPlaceholder(text: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
