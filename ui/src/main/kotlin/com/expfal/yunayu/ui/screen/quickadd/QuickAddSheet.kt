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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.expfal.yunayu.ui.util.formatCents
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

            if (uiState.suggestedTags.isNotEmpty()) {
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
                            label = { Text(tag.name) },
                        )
                    }
                }
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
