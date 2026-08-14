package com.expfal.yunayu.ui.screen.quickadd

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.expfal.yunayu.domain.model.Tag
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
            onConfirm = viewModel::onConfirmNecessary,
            onDismiss = viewModel::onDismissConfirm,
        )
    }

    ModalBottomSheet(
        onDismissRequest = {
            if (!uiState.saving) onDismissRequest()
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 28.dp),
        ) {
            Text(
                text = "¥ " + formatCents(QuickAddViewModel.parseAmountToCents(uiState.amountText) ?: 0L),
                style = MaterialTheme.typography.displayLarge,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                uiState.suggestedTags.forEach { tag ->
                    FilterChip(
                        selected = uiState.selectedTagId == tag.id,
                        onClick = { viewModel.onSelectTag(tag.id) },
                        label = { Text(tagDisplayName(tag, uiState.rootNameById)) },
                    )
                }
                AssistChip(
                    onClick = {
                        viewModel.loadAllTags()
                        showTagPicker = true
                    },
                    label = { Text("更多") },
                )
            }

            Spacer(modifier = Modifier.height(20.dp))
            NumberPad(
                onDigit = viewModel::onDigit,
                onDelete = viewModel::onDelete,
            )

            Spacer(modifier = Modifier.height(20.dp))
            Button(
                onClick = viewModel::onSave,
                enabled = !uiState.saving && QuickAddViewModel.parseAmountToCents(uiState.amountText) != null,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
            ) {
                Text(text = if (uiState.saving) "记一笔中…" else "记一笔")
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

/** 「更多分类」选择层：按根分组展示全部子标签（根标签自身也可选），点选即选中并关闭。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TagPickerSheet(
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
        Spacer(modifier = Modifier.height(8.dp))
        if (allTagsByRoot.isEmpty()) {
            Text(
                text = "暂无可用标签",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
            )
        } else {
            LazyColumn(modifier = Modifier.fillMaxWidth().padding(bottom = 28.dp)) {
                allTagsByRoot.forEach { (root, children) ->
                    item(key = "root-${root.id}") {
                        Text(
                            text = root.name,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                        )
                    }
                    item(key = "self-${root.id}") {
                        TagPickerRow(tag = root, selected = selectedTagId == root.id, onClick = { onSelect(root.id) })
                    }
                    items(children, key = { it.id }) { child ->
                        TagPickerRow(tag = child, selected = selectedTagId == child.id, onClick = { onSelect(child.id) })
                    }
                }
            }
        }
    }
}

/** 选择层中的单个可点选标签行。 */
@Composable
private fun TagPickerRow(tag: Tag, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = tag.name,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        )
        if (selected) {
            Icon(Icons.Default.Check, contentDescription = "已选", tint = MaterialTheme.colorScheme.primary)
        }
    }
}

/** 大额交易的温和二次确认，措辞克制、不说教。 */
@Composable
private fun NecessaryExpenseDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("这笔属于必要支出吗？") },
        text = { Text("只是帮你多确认一下。") },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("记一笔") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("再想想") }
        },
    )
}
