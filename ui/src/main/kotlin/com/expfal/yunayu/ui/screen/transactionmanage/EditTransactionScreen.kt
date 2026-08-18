package com.expfal.yunayu.ui.screen.transactionmanage

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
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
import com.expfal.yunayu.ui.screen.quickadd.NumberPad
import com.expfal.yunayu.ui.util.formatCents
import com.expfal.yunayu.ui.util.vibrateSuccess

/**
 * 「交易编辑」全屏页面：复用记一笔布局逻辑，固定头部+固定底部，键盘弹出时内容不动。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditTransactionScreen(
    transactionId: Long,
    onBack: () -> Unit,
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
                EditTransactionEvent.SaveFailed -> Unit
            }
        }
    }

    BackHandler {
        if (!uiState.saving) onBack()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("编辑记录") },
                navigationIcon = {
                    IconButton(onClick = { if (!uiState.saving) onBack() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回",
                        )
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
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        CircularProgressIndicator()
                    }
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
                    // 固定头部：类型切换 + 金额 + 备注 + 标签 + 账户
                    Column(
                        modifier = Modifier.padding(horizontal = 24.dp),
                    ) {
                        EditTypeToggle(
                            transactionType = uiState.transactionType,
                            onTypeChange = viewModel::setType,
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            text = "¥ " + formatCents(
                                EditTransactionViewModel.parseAmountToCents(uiState.amountText) ?: 0L,
                            ),
                            style = MaterialTheme.typography.displayLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(16.dp))
                        EditNoteField(
                            note = uiState.note,
                            onNoteChange = viewModel::onNoteChange,
                        )
                        Spacer(Modifier.height(12.dp))
                        EditTagChipsRow(
                            selectedTagId = uiState.selectedTagId,
                            selectedTagName = uiState.selectedTagName,
                            onSelectTag = viewModel::onSelectTag,
                            onOpenTagPicker = { showTagPicker = true },
                        )
                        EditAccountChipsRow(
                            accounts = uiState.accounts,
                            selectedAccountId = uiState.selectedAccountId,
                            onSelect = viewModel::onSelectAccount,
                            modifier = Modifier.padding(top = 12.dp),
                        )
                    }

                    // 固定底部：数字键盘 + 保存/取消
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp)
                            .padding(bottom = 28.dp),
                    ) {
                        NumberPad(onDigit = viewModel::onDigit, onDelete = viewModel::onDelete)
                        Spacer(Modifier.height(16.dp))
                        EditActionsRow(
                            saving = uiState.saving,
                            onSave = viewModel::onSave,
                            onCancel = onBack,
                        )
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
