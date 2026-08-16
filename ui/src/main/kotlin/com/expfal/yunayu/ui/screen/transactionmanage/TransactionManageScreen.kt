package com.expfal.yunayu.ui.screen.transactionmanage

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.expfal.yunayu.domain.model.RecentTransaction
import com.expfal.yunayu.domain.model.Tag
import com.expfal.yunayu.ui.component.TagTreeList
import com.expfal.yunayu.ui.component.TransactionRow
import com.expfal.yunayu.ui.screen.organize.OrganizeScreen

/**
 * 「收支管理」全屏：顶部时间 / 标签 / 备注三组筛选，下方交易列表，行尾删除入口。
 *
 * 列表复用 [TransactionRow]，删除经二次确认弹窗后由 ViewModel 执行并发出事件驱动 Snackbar；
 * 标签筛选经 [ModalBottomSheet] 内嵌 [TagTreeList] 多选，点选不关闭弹层、底部「完成」关闭。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionManageScreen(
    onBack: () -> Unit,
    viewModel: TransactionManageViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var showTagSheet by remember { mutableStateOf(false) }
    var showOrganize by remember { mutableStateOf(false) }

    if (showOrganize) {
        OrganizeScreen(onBack = { showOrganize = false })
        return
    }

    BackHandler(onBack = onBack)

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                TransactionManageEvent.Deleted -> snackbarHostState.showSnackbar("已删除")
                TransactionManageEvent.Failed -> snackbarHostState.showSnackbar("删除失败，请重试")
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("收支管理") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    OrganizeAction(
                        count = uiState.uncategorizedCount,
                        onClick = { showOrganize = true },
                    )
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 24.dp),
        ) {
            FilterSection(
                timeRange = uiState.timeRange,
                selectedTagIds = uiState.selectedTagIds,
                keyword = uiState.keyword,
                onTimeRangeSelect = viewModel::selectTimeRange,
                onOpenTagSheet = { showTagSheet = true },
                onKeywordChange = viewModel::onKeywordChange,
            )
            Spacer(Modifier.height(12.dp))
            when {
                uiState.loading -> LoadingState()
                uiState.transactions.isEmpty() -> EmptyState(hasActiveFilter = hasActiveFilter(uiState))
                else -> TransactionList(
                    transactions = uiState.transactions,
                    onDeleteRequest = viewModel::requestDelete,
                )
            }
        }
    }

    if (showTagSheet) {
        TagFilterSheet(
            allTagsByRoot = uiState.allTagsByRoot,
            selectedIds = uiState.selectedTagIds,
            onToggleSelect = viewModel::toggleTagSelection,
            onClear = viewModel::clearTagSelection,
            onDone = { showTagSheet = false },
        )
    }

    uiState.pendingDelete?.let {
        DeleteTransactionDialog(
            onConfirm = viewModel::confirmDelete,
            onDismiss = viewModel::cancelDelete,
        )
    }
}

/** 「整理」入口按钮：文案「整理 N」，无未分类时禁用并隐藏计数。 */
@Composable
private fun OrganizeAction(
    count: Int,
    onClick: () -> Unit,
) {
    TextButton(onClick = onClick, enabled = count > 0) {
        Text(if (count > 0) "整理 $count" else "整理")
    }
}

/** 时间 / 标签 / 备注三组筛选区。 */
@Composable
private fun FilterSection(
    timeRange: TimeFilter,
    selectedTagIds: Set<Long>,
    keyword: String,
    onTimeRangeSelect: (TimeFilter) -> Unit,
    onOpenTagSheet: () -> Unit,
    onKeywordChange: (String) -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TimeFilter.entries.forEach { filter ->
                FilterChip(
                    selected = timeRange == filter,
                    onClick = { onTimeRangeSelect(filter) },
                    label = { Text(filter.label()) },
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        FilterChip(
            selected = selectedTagIds.isNotEmpty(),
            onClick = onOpenTagSheet,
            label = { Text(if (selectedTagIds.isEmpty()) "标签" else "标签（${selectedTagIds.size}）") },
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = keyword,
            onValueChange = onKeywordChange,
            placeholder = { Text("搜索备注关键词") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** 标签多选弹层：内嵌 [TagTreeList]，点选不关闭、底部「完成」关闭，有选中时提供「清除」。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TagFilterSheet(
    allTagsByRoot: Map<Tag, List<Tag>>,
    selectedIds: Set<Long>,
    onToggleSelect: (Long) -> Unit,
    onClear: () -> Unit,
    onDone: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDone) {
        Column(Modifier.fillMaxWidth()) {
            Text(
                text = "按标签筛选",
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
                    selectedIds = selectedIds,
                    onToggleSelect = onToggleSelect,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (selectedIds.isNotEmpty()) {
                    TextButton(onClick = onClear) { Text("清除") }
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onDone) { Text("完成") }
            }
        }
    }
}

/** 交易列表：行尾删除图标入口。 */
@Composable
private fun TransactionList(
    transactions: List<RecentTransaction>,
    onDeleteRequest: (RecentTransaction) -> Unit,
) {
    LazyColumn(Modifier.fillMaxSize()) {
        items(transactions, key = { it.id }) { transaction ->
            TransactionRow(
                transaction = transaction,
                trailing = {
                    IconButton(onClick = { onDeleteRequest(transaction) }) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "删除",
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }
                },
            )
        }
    }
}

/** 删除二次确认弹窗：删除用错误色突出。 */
@Composable
private fun DeleteTransactionDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("删除记录") },
        text = { Text("删除这笔记录？删除后不可恢复") },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("删除", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("再想想") } },
    )
}

/** 加载态占位。 */
@Composable
private fun LoadingState() {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("加载中…", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** 空列表占位：区分「全部无记录」与「筛选后无结果」。 */
@Composable
private fun EmptyState(hasActiveFilter: Boolean) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = if (hasActiveFilter) "没有符合条件的记录" else "暂无记录",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

/** 是否存在生效中的筛选条件。 */
private fun hasActiveFilter(uiState: TransactionManageUiState): Boolean =
    uiState.timeRange != TimeFilter.ALL || uiState.selectedTagIds.isNotEmpty() || uiState.keyword.isNotBlank()

/** 时间筛选维度展示文案。 */
private fun TimeFilter.label(): String = when (this) {
    TimeFilter.ALL -> "全部"
    TimeFilter.LAST_7_DAYS -> "近7天"
    TimeFilter.LAST_30_DAYS -> "近30天"
    TimeFilter.THIS_MONTH -> "本月"
}
