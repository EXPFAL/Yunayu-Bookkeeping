package com.expfal.yunayu.ui.screen.quickadd

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
 * 「记一笔」全屏页面：替代原 ModalBottomSheet 弹层，彻底解决键盘遮挡问题。
 *
 * 复用 [QuickAddViewModel] 业务逻辑，上部内容区可滚动，下部固定输入区，
 * 键盘弹出时通过 [.imePadding] 标准 insets 链路自适应。
 *
 * @param onBack 返回首页回调（放弃输入或保存成功后触发）
 * @param onSaved 保存成功后回调（触发首页自动滚动）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickAddScreen(
    onBack: () -> Unit,
    onSaved: () -> Unit,
    viewModel: QuickAddViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showTagPicker by remember { mutableStateOf(false) }

    // F2 修复：合成期同步一次性重置，避免 LaunchedEffect 首帧后才执行导致闪现陈旧状态
    val resetDone = remember { mutableStateOf(false) }
    if (!resetDone.value) {
        viewModel.resetForOpen()
        resetDone.value = true
    }

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
                QuickAddEvent.SaveFailed -> Unit
            }
        }
    }

    // F1 修复：BackHandler 常开，守卫移入回调，对齐其他 5 个全屏页的常开写法
    // 避免 saving/nlParsing 时 handler 被禁用导致返回事件穿透到 Activity finish 退出应用
    BackHandler {
        if (!uiState.saving && !uiState.nlParsing) onBack()
    }

    // F5 修复：抽离大额确认对话框条件渲染块，使主函数落回 ≤80 行
    ConfirmDialogIfNeeded(uiState = uiState, viewModel = viewModel)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("记一笔") },
                navigationIcon = {
                    IconButton(
                        onClick = { if (!uiState.saving && !uiState.nlParsing) onBack() },
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回",
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        QuickAddScreenContent(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding(),
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

/** 大额确认对话框条件渲染（抽离以控制主函数行数）。 */
@Composable
private fun ConfirmDialogIfNeeded(
    uiState: QuickAddUiState,
    viewModel: QuickAddViewModel,
) {
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
}

/**
 * 全屏页面内容区：上部可滚动表单 + 下部固定输入区。
 *
 * F4 退化策略：当可用高度低于固定区+最小内容区阈值时（如小屏/横屏），
 * 整页退化为单一 verticalScroll 布局，固定区并入滚动，避免裁剪。
 * 正常大屏（如 K80 Ultra 竖屏）保持现有固定分区。
 */
@Composable
private fun QuickAddScreenContent(
    modifier: Modifier,
    uiState: QuickAddUiState,
    viewModel: QuickAddViewModel,
    onShowTagPicker: () -> Unit,
) {
    // 固定区预估高度：TopAppBar(64) + NumberPad(252) + Button(52) + padding(60) = 428dp
    // 最小内容区：NlModeToggle(32) + TypeToggle(32) + AmountDisplay(48) + padding(40) = 152dp
    // 阈值 = 428 + 152 = 580dp，低于此值退化为单一滚动布局
    val degradationThresholdDp = 580.dp

    BoxWithConstraints(modifier = modifier) {
        val isCompact = maxHeight < degradationThresholdDp
        if (isCompact) {
            // 退化策略：单一滚动布局，固定区并入滚动
            CompactLayout(
                uiState = uiState,
                viewModel = viewModel,
                onShowTagPicker = onShowTagPicker,
            )
        } else {
            // 正常布局：上部可滚动 + 下部固定
            StandardLayout(
                uiState = uiState,
                viewModel = viewModel,
                onShowTagPicker = onShowTagPicker,
            )
        }
    }
}

/** 标准布局：上部内容区 + 下部固定输入区。
 *
 * 手动记模式：上部可滚动（类型切换/金额/标签/账户/备注），下部固定数字键盘。
 * 自动记模式：上部不滚动（模式切换/标签/账户/NL输入框 + 下方空白），下部无固定区。
 * 键盘弹出时 imePadding 收缩下方空白，仅覆盖空白、不遮挡输入框。
 */
@Composable
private fun StandardLayout(
    uiState: QuickAddUiState,
    viewModel: QuickAddViewModel,
    onShowTagPicker: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .weight(1f)
                // 自动记模式不走 verticalScroll，避免键盘弹出时 bringIntoView 滚动
                .then(if (uiState.nlMode) Modifier else Modifier.verticalScroll(rememberScrollState()))
                .padding(horizontal = 24.dp),
        ) {
            QuickAddFormContent(
                uiState = uiState,
                viewModel = viewModel,
                onShowTagPicker = onShowTagPicker,
            )
            // 自动记模式：NL 输入框+解析按钮上移至页面中部，下方空白供 imePadding 收缩
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
                // 下方空白：键盘弹出时 imePadding 收缩此空间，仅覆盖空白不遮挡输入框
                Spacer(modifier = Modifier.weight(1f))
            }
        }
        // 手动记模式：下部固定数字键盘+保存按钮；自动记模式无固定区
        if (!uiState.nlMode) {
            FixedInputSection(
                uiState = uiState,
                viewModel = viewModel,
            )
        }
        FeedbackSection(uiState = uiState)
    }
}

/**
 * 退化布局：单一滚动布局，固定区并入滚动，避免小屏/横屏裁剪。
 *
 * 自动记模式：NL 输入框+解析按钮紧随标签/账户选择，下方保留空白（退化模式下滚动容器处理）。
 * 手动记模式：数字键盘+保存按钮紧随备注输入框。
 */
@Composable
private fun CompactLayout(
    uiState: QuickAddUiState,
    viewModel: QuickAddViewModel,
    onShowTagPicker: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
    ) {
        QuickAddFormContent(
            uiState = uiState,
            viewModel = viewModel,
            onShowTagPicker = onShowTagPicker,
        )
        Spacer(modifier = Modifier.height(16.dp))
        // 自动记模式：NL 输入框+解析按钮紧随内容
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
            // 退化模式下保留底部空白，保持视觉一致性
            Spacer(modifier = Modifier.height(100.dp))
        } else {
            // 手动记模式：数字键盘+保存按钮
            FixedInputSection(
                uiState = uiState,
                viewModel = viewModel,
            )
        }
        FeedbackSection(uiState = uiState)
        Spacer(modifier = Modifier.height(16.dp))
    }
}

/** 表单内容区：模式切换 + 类型切换 + 金额 + 标签 + 账户 + 备注。 */
@Composable
private fun QuickAddFormContent(
    uiState: QuickAddUiState,
    viewModel: QuickAddViewModel,
    onShowTagPicker: () -> Unit,
) {
    NlModeToggle(
        nlMode = uiState.nlMode,
        onModeChange = viewModel::setNlMode,
    )
    Spacer(modifier = Modifier.height(16.dp))
    if (!uiState.nlMode) {
        TypeToggle(
            transactionType = uiState.transactionType,
            transferMode = uiState.transferMode,
            onTypeChange = viewModel::setType,
            onTransfer = viewModel::setTransferMode,
        )
        // 优化项 1：金额上下间距统一 24dp
        Spacer(modifier = Modifier.height(24.dp))
        AmountDisplay(amountText = uiState.amountText)
        Spacer(modifier = Modifier.height(24.dp))
    }
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
    if (!uiState.nlMode && !uiState.transferMode) {
        ManualNoteField(
            note = uiState.manualNote,
            onNoteChange = viewModel::onManualNoteChange,
        )
    }
}

/** 金额大字显示（仅手动记模式）。 */
@Composable
private fun AmountDisplay(amountText: String) {
    Text(
        text = "¥ " + formatCents(QuickAddViewModel.parseAmountToCents(amountText) ?: 0L),
        style = MaterialTheme.typography.displayLarge,
        color = MaterialTheme.colorScheme.onSurface,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
}

/** 手动记模式的备注输入框（转账模式隐藏）。 */
@Composable
private fun ManualNoteField(
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
    Spacer(modifier = Modifier.height(12.dp))
}

/** 数字模式三段式切换控件：支出 / 收入 / 转账。 */
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

/** 收支表单分支块：最近分类 chips + 账户选择。 */
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

/**
 * 下部固定输入区：手动记=数字键盘+保存按钮。
 *
 * 注：自动记模式的 NL 输入框+解析按钮已上移至 StandardLayout/CompactLayout 的内容区，
 * 此函数仅处理手动记模式，自动记模式调用时不应到达此处（由调用方守卫）。
 */
@Composable
private fun FixedInputSection(
    uiState: QuickAddUiState,
    viewModel: QuickAddViewModel,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .padding(bottom = 28.dp),
    ) {
        // 手动记模式：数字键盘+保存按钮
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
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
        )
    }
    if (uiState.saveFailed) {
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "刚才没记上，再试一次",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
        )
    }
}

/** 大额交易的温和二次确认。 */
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

/** 「更多分类」选择层：按根分组折叠展示子标签。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TagPickerSheet(
    allTagsByRoot: Map<Tag, List<Tag>>,
    selectedTagId: Long?,
    onSelect: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    androidx.compose.material3.ModalBottomSheet(onDismissRequest = onDismiss) {
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
            TagTreeList(
                allTagsByRoot = allTagsByRoot,
                selectedIds = setOfNotNull(selectedTagId),
                onToggleSelect = onSelect,
                modifier = Modifier.padding(bottom = 28.dp),
            )
        }
    }
}

/** 账户选择横向 chips：首位固定「未指定」+ 各账户，单选互斥。 */
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

/** 转账模式表单：转出账户 + 转入账户 + 备注输入。 */
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
