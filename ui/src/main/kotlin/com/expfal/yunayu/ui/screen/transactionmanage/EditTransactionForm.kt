package com.expfal.yunayu.ui.screen.transactionmanage

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.expfal.yunayu.domain.model.Account
import com.expfal.yunayu.domain.model.Tag
import com.expfal.yunayu.domain.model.TransactionType
import com.expfal.yunayu.ui.component.TagTreeList

/**
 * 交易编辑表单控件：从已删除的 EditTransactionSheet 抽出，供 [EditTransactionScreen] 复用。
 */
/** 收/支方向切换控件，样式与快捷记账的 [com.expfal.yunayu.ui.screen.quickadd.QuickAddScreen] 对齐。 */
@Composable
internal fun EditTypeToggle(
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
internal fun EditNoteField(
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
internal fun EditTagChipsRow(
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

/** 账户选择横向 chips：各账户在前，「未指定」固定末位，单选互斥；账户列表为空时整行不渲染。 */
@Composable
internal fun EditAccountChipsRow(
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
        accounts.forEach { account ->
            FilterChip(
                selected = selectedAccountId == account.id,
                onClick = { onSelect(account.id) },
                label = { Text(account.name) },
            )
        }
        FilterChip(
            selected = selectedAccountId == null,
            onClick = { onSelect(null) },
            label = { Text("未指定") },
        )
    }
}

/** 保存 / 取消操作行。 */
@Composable
internal fun EditActionsRow(
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
internal fun EditTagPickerSheet(
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
