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
        val isIncome = if (uiState.nlMode) {
            uiState.nlDraft?.type == TransactionType.INCOME
        } else {
            uiState.transactionType == TransactionType.INCOME
        }
        NecessaryExpenseDialog(
            isIncome = isIncome,
            onConfirm = viewModel::onConfirmNecessary,
            onDismiss = viewModel::onDismissConfirm,
        )
    }

    ModalBottomSheet(
        onDismissRequest = {
            if (!uiState.saving && !uiState.nlParsing) onDismissRequest()
        },
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
            if (!uiState.nlMode) {
                TypeToggle(
                    transactionType = uiState.transactionType,
                    onTypeChange = viewModel::setType,
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
                        val type = if (uiState.nlMode) {
                            uiState.nlDraft?.type ?: TransactionType.EXPENSE
                        } else {
                            uiState.transactionType
                        }
                        viewModel.loadAllTags(type)
                        showTagPicker = true
                    },
                    label = { Text("更多") },
                )
            }

            Spacer(modifier = Modifier.height(20.dp))
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

/** 「更多分类」选择层：按根分组折叠展示子标签（根标签自身也可选），点选即选中并关闭。 */
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

/** 数字模式收/支方向切换控件：仅数字模式展示，样式与 [NlModeToggle] 一致。 */
@Composable
private fun TypeToggle(
    transactionType: TransactionType,
    onTypeChange: (TransactionType) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
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
