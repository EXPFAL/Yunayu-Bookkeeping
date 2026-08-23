package com.expfal.yunayu.ui.screen.tagmanage

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.expfal.yunayu.domain.model.Tag
import com.expfal.yunayu.domain.model.TagDeleteImpact
import com.expfal.yunayu.ui.util.moveItem
import com.expfal.yunayu.ui.util.reorderTargetIndex
import kotlinx.coroutines.flow.collect

private const val ENTRY_TYPE_HEADER = 0
private const val ENTRY_TYPE_CHILD = 1

/** 标签行固定高度，拖拽换算目标索引与 [Modifier.height] 使用同一常量，保证行高口径一致。 */
private val rowHeight = 56.dp

/** 扁平化列表项：根分区头或子标签行，供单个 [LazyColumn.items] 渲染以降低滑动重组开销。 */
private sealed interface TagListEntry {
    val key: Any

    data class Header(val root: Tag) : TagListEntry {
        override val key: Any get() = "header-${root.id}"
    }

    data class Child(val rootId: Long, val tag: Tag, val index: Int) : TagListEntry {
        override val key: Any get() = tag.id
    }
}

/**
 * 「学业关联标签」管理全屏：根标签只读分区，子标签支持增/改/删与同分区长按拖拽排序。
 *
 * 内容为单个 [LazyColumn]（根头 + 子标签平铺），拖拽期间以本地 [dragList] 门控
 * 观察链重发射覆盖，结束后经 [TagManageViewModel.onReorder] 乐观提交并在失败时回滚。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TagManageScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TagManageViewModel = viewModel(),
) {
    val tagListUiState by viewModel.listUiState.collectAsStateWithLifecycle()
    val mergeState by viewModel.mergeUiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val listState = rememberLazyListState()
    val itemHeightPx = with(LocalDensity.current) { rowHeight.roundToPx() }

    var draggingParentId by remember { mutableStateOf<Long?>(null) }
    var draggingItemId by remember { mutableStateOf<Long?>(null) }
    var dragList by remember { mutableStateOf<List<Tag>>(emptyList()) }
    var dragStartIndex by remember { mutableStateOf(0) }
    var dragCurrentIndex by remember { mutableStateOf(0) }
    var dragOffsetY by remember { mutableStateOf(0f) }

    var addRoot by remember { mutableStateOf<Tag?>(null) }
    var addSubmitted by remember { mutableStateOf(false) }
    var showMergeSheet by remember { mutableStateOf(false) }

    val handleBack = {
        viewModel.cancelDelete()
        viewModel.dismissRename()
        viewModel.clearError()
        onBack()
    }
    BackHandler(onBack = handleBack)

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                TagManageEvent.Deleted -> snackbarHostState.showSnackbar("已删除")
                TagManageEvent.Failed -> snackbarHostState.showSnackbar("操作失败，请重试")
                is TagManageEvent.Merged -> snackbarHostState.showSnackbar(
                    "已合并：${event.affectedTransactionCount} 条记录并入「${event.keepTagName}」",
                )
                TagManageEvent.MergeFailed -> snackbarHostState.showSnackbar("合并失败，请重试")
            }
        }
    }

    LaunchedEffect(tagListUiState.busy, tagListUiState.errorMessage) {
        if (addSubmitted && !tagListUiState.busy) {
            if (tagListUiState.errorMessage == null) addRoot = null
            addSubmitted = false
        }
    }

    val roots = tagListUiState.roots
    val childrenByRoot = tagListUiState.childrenByRoot

    fun clearDrag() {
        draggingParentId = null
        draggingItemId = null
        dragList = emptyList()
        dragStartIndex = 0
        dragCurrentIndex = 0
        dragOffsetY = 0f
    }

    val entries = remember(roots, childrenByRoot, draggingParentId, dragList) {
        buildTagListEntries(roots, childrenByRoot, draggingParentId, dragList)
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("标签管理") },
                navigationIcon = {
                    IconButton(onClick = handleBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    TextButton(
                        onClick = {
                            showMergeSheet = true
                            viewModel.detectMergeCandidates()
                        },
                    ) {
                        Text("整合")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        TagManageTagList(
            entries = entries,
            listState = listState,
            innerPadding = innerPadding,
            loading = tagListUiState.loading && roots.isEmpty(),
            draggingItemId = draggingItemId,
            onAddSubTag = { root ->
                addRoot = root
                addSubmitted = false
                viewModel.clearError()
            },
            onDragStart = { rootId, tag, index ->
                val children = tagListUiState.childrenByRoot[rootId].orEmpty()
                draggingParentId = rootId
                draggingItemId = tag.id
                dragList = children
                dragStartIndex = index
                dragCurrentIndex = index
                dragOffsetY = 0f
            },
            onDrag = { amount ->
                dragOffsetY += amount
                val delta = reorderTargetIndex(dragList.size, itemHeightPx, dragOffsetY)
                val target = (dragStartIndex + delta).coerceIn(0, dragList.lastIndex)
                if (dragCurrentIndex != target) {
                    dragList = moveItem(dragList, dragCurrentIndex, target)
                    dragCurrentIndex = target
                }
            },
            onDragEnd = { rootId ->
                if (dragList != tagListUiState.childrenByRoot[rootId].orEmpty()) {
                    viewModel.onReorder(rootId, dragList)
                }
                clearDrag()
            },
            onDragCancel = { clearDrag() },
            onRename = viewModel::requestRename,
            onDelete = viewModel::requestDelete,
        )
    }

    addRoot?.let { root ->
        AddTagDialog(
            root = root,
            errorMessage = tagListUiState.errorMessage,
            onConfirm = { name ->
                addSubmitted = true
                viewModel.addSubTag(root.id, name)
            },
            onDismiss = {
                addRoot = null
                viewModel.clearError()
            },
        )
    }

    tagListUiState.renamingTag?.let { tag ->
        RenameDialog(
            tag = tag,
            errorMessage = tagListUiState.errorMessage,
            onConfirm = { name -> viewModel.rename(tag.id, name) },
            onDismiss = { viewModel.dismissRename() },
        )
    }

    tagListUiState.pendingDelete?.let { (tag, impact) ->
        DeleteConfirmDialog(
            tag = tag,
            impact = impact,
            onConfirm = { viewModel.confirmDelete() },
            onDismiss = { viewModel.cancelDelete() },
        )
    }

    if (showMergeSheet) {
        TagMergeSheet(
            mergeState = mergeState,
            onChoiceSelected = viewModel::setMergeChoice,
            onMerge = viewModel::confirmMerge,
            onRetryDetect = viewModel::detectMergeCandidates,
            onDismiss = { showMergeSheet = false },
        )
    }
}

@Immutable
private data class TagListEntries(val items: List<TagListEntry>)

private fun buildTagListEntries(
    roots: List<Tag>,
    childrenByRoot: Map<Long, List<Tag>>,
    draggingParentId: Long?,
    dragList: List<Tag>,
): TagListEntries = TagListEntries(
    buildList {
        roots.forEach { root ->
            add(TagListEntry.Header(root))
            val children = if (draggingParentId == root.id) dragList else childrenByRoot[root.id].orEmpty()
            children.forEachIndexed { index, tag ->
                add(TagListEntry.Child(root.id, tag, index))
            }
        }
    },
)

/** 标签列表主体：与弹窗/整合状态解耦，减少无关字段变更触发的列表重组。 */
@Composable
private fun TagManageTagList(
    entries: TagListEntries,
    listState: LazyListState,
    innerPadding: androidx.compose.foundation.layout.PaddingValues,
    loading: Boolean,
    draggingItemId: Long?,
    onAddSubTag: (Tag) -> Unit,
    onDragStart: (rootId: Long, tag: Tag, index: Int) -> Unit,
    onDrag: (Float) -> Unit,
    onDragEnd: (rootId: Long) -> Unit,
    onDragCancel: () -> Unit,
    onRename: (Tag) -> Unit,
    onDelete: (Tag) -> Unit,
) {
    var dragGesturesReady by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(32)
        dragGesturesReady = true
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().padding(innerPadding),
    ) {
        if (loading && entries.items.isEmpty()) {
            item(key = "loading") {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }
        }
        items(
            items = entries.items,
            key = { it.key },
            contentType = { entry ->
                when (entry) {
                    is TagListEntry.Header -> ENTRY_TYPE_HEADER
                    is TagListEntry.Child -> ENTRY_TYPE_CHILD
                }
            },
        ) { entry ->
            when (entry) {
                is TagListEntry.Header -> RootHeader(entry.root, onAdd = { onAddSubTag(entry.root) })
                is TagListEntry.Child -> TagRow(
                    tag = entry.tag,
                    isDragging = draggingItemId == entry.tag.id,
                    dragGesturesReady = dragGesturesReady,
                    onDragStart = { onDragStart(entry.rootId, entry.tag, entry.index) },
                    onDrag = onDrag,
                    onDragEnd = { onDragEnd(entry.rootId) },
                    onDragCancel = onDragCancel,
                    onRename = { onRename(entry.tag) },
                    onDelete = { onDelete(entry.tag) },
                )
            }
        }
    }
}

/** 根标签分区头：icon + 根名（只读）+ 添加子标签入口。 */
@Composable
private fun RootHeader(root: Tag, onAdd: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        root.icon?.let { Text(it, style = MaterialTheme.typography.titleMedium) }
        Spacer(Modifier.width(8.dp))
        Text(
            root.name,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        TextButton(onClick = onAdd) {
            Icon(Icons.Default.Add, contentDescription = null)
            Spacer(Modifier.width(4.dp))
            Text("添加")
        }
    }
}

/** 子标签行：拖拽手柄 + 名称 + 改名/删除入口。 */
@Composable
private fun TagRow(
    tag: Tag,
    isDragging: Boolean,
    dragGesturesReady: Boolean,
    onDragStart: () -> Unit,
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(rowHeight)
            .background(if (isDragging) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface)
            .padding(horizontal = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DragHandle(
            tag = tag,
            dragGesturesReady = dragGesturesReady,
            onDragStart = onDragStart,
            onDrag = onDrag,
            onDragEnd = onDragEnd,
            onDragCancel = onDragCancel,
        )
        Spacer(Modifier.width(16.dp))
        Text(tag.name, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        IconButton(onClick = onRename) {
            Icon(Icons.Default.Edit, contentDescription = "改名")
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Default.Delete, contentDescription = "删除", tint = MaterialTheme.colorScheme.error)
        }
    }
}

/** 长按拖拽手柄：长按后拖动回调累计偏移，供父级换算目标索引并重排本地列表。 */
@Composable
private fun DragHandle(
    tag: Tag,
    dragGesturesReady: Boolean,
    onDragStart: () -> Unit,
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit,
) {
    val currentOnDragStart by rememberUpdatedState(onDragStart)
    val currentOnDrag by rememberUpdatedState(onDrag)
    val currentOnDragEnd by rememberUpdatedState(onDragEnd)
    val currentOnDragCancel by rememberUpdatedState(onDragCancel)

    Icon(
        imageVector = Icons.Default.Menu,
        contentDescription = "拖拽排序",
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = if (dragGesturesReady) {
            Modifier.pointerInput(tag.id) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { currentOnDragStart() },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        currentOnDrag(dragAmount.y)
                    },
                    onDragEnd = { currentOnDragEnd() },
                    onDragCancel = { currentOnDragCancel() },
                )
            }
        } else {
            Modifier
        },
    )
}

/** 添加子标签弹窗：输入 + 错误文案，空名禁用确认。 */
@Composable
private fun AddTagDialog(
    root: Tag,
    errorMessage: String?,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("在「${root.name}」下添加子标签") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("标签名") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (errorMessage != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(errorMessage, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name) }, enabled = name.isNotBlank()) { Text("添加") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

/** 改名弹窗：预填当前名，空名禁用确认，失败透出错误文案。 */
@Composable
private fun RenameDialog(
    tag: Tag,
    errorMessage: String?,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(tag.name) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("重命名标签") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("标签名") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (errorMessage != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(errorMessage, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name) }, enabled = name.isNotBlank()) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

/** 删除二次确认弹窗：展示子树规模与受影响交易数，删除用错误色突出。 */
@Composable
private fun DeleteConfirmDialog(
    tag: Tag,
    impact: TagDeleteImpact,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val childrenCount = (impact.subtreeNodeCount - 1).coerceAtLeast(0)
    val childNames = impact.subtreeNames.drop(1)
    val nameSnippet = when {
        childNames.isEmpty() -> ""
        childNames.size > 3 -> "（${childNames.take(3).joinToString("、")} 等）"
        else -> "（${childNames.joinToString("、")}）"
    }
    val message = buildString {
        if (childrenCount > 0) {
            append("删除「${tag.name}」会同时删除 $childrenCount 个子标签")
            append(nameSnippet)
            append("，")
        }
        append("${impact.affectedTransactionCount} 笔记账将变为未分类，确定吗？")
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("删除标签") },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("删除", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("再想想") } },
    )
}
