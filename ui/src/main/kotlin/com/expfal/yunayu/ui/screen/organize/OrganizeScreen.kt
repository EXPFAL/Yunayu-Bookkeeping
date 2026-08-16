package com.expfal.yunayu.ui.screen.organize

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import com.expfal.yunayu.domain.nl.model.Action
import com.expfal.yunayu.domain.nl.model.OrganizeSuggestion
import com.expfal.yunayu.ui.component.TagTreeList
import com.expfal.yunayu.ui.util.formatSignedCents
import com.expfal.yunayu.ui.util.formatTime
import com.expfal.yunayu.ui.util.tagDisplayName

/**
 * 「整理未分类」全屏：进入自动开始分批建议，SUGGESTING 显示进度，REVIEWING 逐条接受 / 修改 / 拒绝，
 * 底部「应用」落库，DONE 展示结果摘要并支持失败重试，未配置 API 时给出对齐报告页的提示。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrganizeScreen(
    onBack: () -> Unit,
    viewModel: OrganizeViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var modifyRecordId by remember { mutableStateOf<Long?>(null) }

    BackHandler(onBack = onBack)

    LaunchedEffect(Unit) { viewModel.start() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("整理未分类") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            when (uiState.phase) {
                OrganizePhase.SUGGESTING -> SuggestingContent(
                    uiState = uiState,
                    onCancel = {
                        viewModel.cancel()
                        onBack()
                    },
                )
                OrganizePhase.REVIEWING -> ReviewingContent(
                    uiState = uiState,
                    onDecision = viewModel::setDecision,
                    onModify = { modifyRecordId = it },
                    onApply = viewModel::apply,
                )
                OrganizePhase.APPLYING -> ApplyingContent()
                OrganizePhase.DONE -> DoneContent(
                    uiState = uiState,
                    onBack = onBack,
                    onRetry = viewModel::retryFailed,
                )
                OrganizePhase.ERROR_NO_API -> ErrorNoApiContent()
                OrganizePhase.IDLE -> IdleContent()
            }
        }
    }

    modifyRecordId?.let { recordId ->
        ModifyTagSheet(
            allTagsByRoot = uiState.allTagsByRoot,
            selectedTagId = uiState.suggestions
                .firstOrNull { it.record.id == recordId }
                ?.modifiedTagId,
            onSelect = { tagId, displayName ->
                viewModel.modifyTarget(recordId, tagId, displayName)
                modifyRecordId = null
            },
            onDismiss = { modifyRecordId = null },
        )
    }
}

/** 建议进行中：进度条 + 批进度文案 + 取消按钮。 */
@Composable
private fun SuggestingContent(
    uiState: OrganizeUiState,
    onCancel: () -> Unit,
) {
    val progress = if (uiState.totalBatches == 0) {
        0f
    } else {
        uiState.doneBatches.toFloat() / uiState.totalBatches
    }
    val processed = minOf(uiState.doneBatches * OrganizeViewModel.BATCH_SIZE, uiState.totalRecords)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("正在分析未分类记录", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(16.dp))
        LinearProgressIndicator(
            progress = { progress.coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "正在分析 $processed/${uiState.totalRecords} 条",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))
        TextButton(onClick = onCancel) { Text("取消") }
    }
}

/** 审核列表：逐条建议行 + 底部应用栏；无任何建议时给空态提示。 */
@Composable
private fun ReviewingContent(
    uiState: OrganizeUiState,
    onDecision: (Long, OrganizeDecision) -> Unit,
    onModify: (Long) -> Unit,
    onApply: () -> Unit,
) {
    val rejectedCount = uiState.suggestions.count { it.decision == OrganizeDecision.REJECT }
    val appliedCount = uiState.suggestions.size - rejectedCount
    Column(Modifier.fillMaxSize()) {
        if (uiState.suggestions.isEmpty()) {
            EmptySuggestions()
        } else {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 12.dp),
            ) {
                items(uiState.suggestions, key = { it.record.id }) { item ->
                    OrganizeItemRow(
                        item = item,
                        onDecision = onDecision,
                        onModify = onModify,
                    )
                }
            }
        }
        ApplyBar(
            appliedCount = appliedCount,
            rejectedCount = rejectedCount,
            busy = uiState.busy,
            onApply = onApply,
        )
    }
}

/** 单条建议卡：记录摘要 + 建议 chip + 接受 / 修改 / 拒绝按钮组。 */
@Composable
private fun OrganizeItemRow(
    item: OrganizeItemUi,
    onDecision: (Long, OrganizeDecision) -> Unit,
    onModify: (Long) -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            RecordSummary(item.record)
            Spacer(Modifier.height(8.dp))
            SuggestionChip(item)
            Spacer(Modifier.height(8.dp))
            DecisionButtons(item = item, onDecision = onDecision, onModify = onModify)
        }
    }
}

/** 记录摘要：优先备注，无备注显示金额 + 时间。 */
@Composable
private fun RecordSummary(record: RecentTransaction) {
    val text = record.note?.takeIf { it.isNotBlank() }
        ?: "${formatSignedCents(record.amountCents, record.type)} ${formatTime(record.occurredAt)}"
    Text(text = text, style = MaterialTheme.typography.bodyMedium)
}

/** 建议标签 chip：按决定显示建议 / 修改目标 / 已拒绝。 */
@Composable
private fun SuggestionChip(item: OrganizeItemUi) {
    val label = when (item.decision) {
        OrganizeDecision.ACCEPT -> suggestionLabel(item.suggestion)
        OrganizeDecision.MODIFY -> item.modifiedTagName ?: suggestionLabel(item.suggestion)
        OrganizeDecision.REJECT -> "已拒绝"
    }
    val color = if (item.decision == OrganizeDecision.REJECT) {
        MaterialTheme.colorScheme.surfaceVariant
    } else {
        MaterialTheme.colorScheme.primaryContainer
    }
    Surface(shape = MaterialTheme.shapes.small, color = color) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = if (item.decision == OrganizeDecision.REJECT) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.onPrimaryContainer
            },
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

/** 接受 / 修改 / 拒绝三按钮组，选中态区分。 */
@Composable
private fun DecisionButtons(
    item: OrganizeItemUi,
    onDecision: (Long, OrganizeDecision) -> Unit,
    onModify: (Long) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(
            selected = item.decision == OrganizeDecision.ACCEPT,
            onClick = { onDecision(item.record.id, OrganizeDecision.ACCEPT) },
            label = { Text("接受") },
        )
        FilterChip(
            selected = item.decision == OrganizeDecision.MODIFY,
            onClick = { onModify(item.record.id) },
            label = { Text("修改") },
        )
        FilterChip(
            selected = item.decision == OrganizeDecision.REJECT,
            onClick = { onDecision(item.record.id, OrganizeDecision.REJECT) },
            label = { Text("拒绝") },
        )
    }
}

/** 底部应用栏：已拒绝计数 + 「应用 N 条」确认按钮（busy 或无待应用项时禁用）。 */
@Composable
private fun ApplyBar(
    appliedCount: Int,
    rejectedCount: Int,
    busy: Boolean,
    onApply: () -> Unit,
) {
    Surface(shadowElevation = 8.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (rejectedCount > 0) {
                Text(
                    text = "已拒绝 $rejectedCount 条",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.weight(1f))
            Button(
                onClick = onApply,
                enabled = appliedCount > 0 && !busy,
            ) {
                Text("应用 $appliedCount 条")
            }
        }
    }
}

/** 应用进行中占位。 */
@Composable
private fun ApplyingContent() {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator()
        Spacer(Modifier.height(12.dp))
        Text("正在应用标签…", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** 完成态：成功 / 失败摘要 + 失败重试 + 返回。 */
@Composable
private fun DoneContent(
    uiState: OrganizeUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
) {
    val failedCount = uiState.failedRecordIds.size
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("整理完成", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Text(
            text = if (failedCount > 0) {
                "成功 ${uiState.appliedCount} 条，失败 $failedCount 条保留未分类"
            } else {
                "成功 ${uiState.appliedCount} 条"
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (uiState.mergeHintCount > 0) {
            Spacer(Modifier.height(12.dp))
            Text(
                text = "检测到 ${uiState.mergeHintCount} 对疑似重复标签，可前往标签管理页整合",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
            )
        }
        if (uiState.reusedTagNames.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Text(
                text = "${uiState.reusedTagNames.size} 个建议因同名标签已存在，已挂载至既有标签：" +
                    uiState.reusedTagNames.joinToString("、"),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        if (failedCount > 0) {
            Spacer(Modifier.height(16.dp))
            Button(onClick = onRetry) { Text("重试失败项") }
        }
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onBack) { Text("返回") }
    }
}

/** 未配置 API 提示，口径对齐报告页。 */
@Composable
private fun ErrorNoApiContent() {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "未配置 API，整理不可用",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** 入口前 / 取消后的瞬时占位。 */
@Composable
private fun IdleContent() {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator()
    }
}

/** 无任何建议的空态：未分类记录保持原样。 */
@Composable
private fun EmptySuggestions() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "没有生成任何标签建议，未分类记录保持原样",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

/** 修改目标选择弹层：内嵌 [TagTreeList] 单选，点选即回填并关闭。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModifyTagSheet(
    allTagsByRoot: Map<Tag, List<Tag>>,
    selectedTagId: Long?,
    onSelect: (Long, String) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth()) {
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
                    onToggleSelect = { tagId ->
                        resolveDisplayName(allTagsByRoot, tagId)?.let { displayName ->
                            onSelect(tagId, displayName)
                        }
                    },
                    modifier = Modifier.padding(bottom = 28.dp),
                )
            }
        }
    }
}

/** 建议展示文案：ATTACH 用标签名，CREATE 标注「新建」。 */
private fun suggestionLabel(suggestion: OrganizeSuggestion): String =
    if (suggestion.action == Action.CREATE) {
        "新建 ${suggestion.tagName}（${suggestion.rootName.orEmpty()}）"
    } else {
        suggestion.tagName
    }

/** 由标签 id 反查展示名：根返回裸名，子返回「根·子」全名。 */
private fun resolveDisplayName(allTagsByRoot: Map<Tag, List<Tag>>, tagId: Long): String? {
    val rootNameById = allTagsByRoot.keys.associate { it.id to it.name }
    allTagsByRoot.entries.forEach { (root, children) ->
        if (root.id == tagId) return root.name
        children.firstOrNull { it.id == tagId }?.let { return tagDisplayName(it, rootNameById) }
    }
    return null
}
