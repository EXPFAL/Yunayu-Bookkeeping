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
import com.expfal.yunayu.ui.util.vibrateSuccess
import java.util.Locale

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

    LaunchedEffect(viewModel, context) {
        viewModel.events.collect { event ->
            when (event) {
                QuickAddEvent.Saved -> {
                    context.vibrateSuccess()
                    onSaved()
                }
            }
        }
    }

    if (uiState.confirmRequested) {
        NecessaryExpenseDialog(
            onConfirm = viewModel::onConfirmNecessary,
            onDismiss = viewModel::onDismissConfirm,
        )
    }

    ModalBottomSheet(onDismissRequest = onDismissRequest) {
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
                enabled = !uiState.saving,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
            ) {
                Text(text = if (uiState.saving) "记下中…" else "记下")
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
        text = { Text("只是帮你多确认一下，记下后随时能改。") },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("记下") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("再想想") }
        },
    )
}

/** 将「分」格式化为两位小数的元，如 1250 → "12.50"。 */
private fun formatCents(cents: Long): String = String.format(Locale.US, "%.2f", cents / 100.0)
