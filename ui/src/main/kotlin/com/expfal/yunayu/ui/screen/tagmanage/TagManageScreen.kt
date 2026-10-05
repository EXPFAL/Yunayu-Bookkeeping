package com.expfal.yunayu.ui.screen.tagmanage

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.expfal.yunayu.domain.model.Tag
import com.expfal.yunayu.domain.model.TagDeleteImpact
import com.expfal.yunayu.ui.util.moveItem
import com.expfal.yunayu.ui.util.reorderTargetIndex
import kotlinx.coroutines.withTimeoutOrNull

/** 标签行固定高度，拖拽换算目标索引与 [Modifier.height] 使用同一常量，保证行高口径一致。 */
private val rowHeight = 56.dp

/** 扁平列表项：根分区头或子标签行，供单个 [LazyColumn] 使用。 */
private sealed interface TagListItem {
    val key: Any
    val contentType: String

    data class Header(val root: Tag) : TagListItem {
        override val key: Any get() = "header-${root.id}"
        override val contentType: String get() = "header"
    }

    data class Child(
        val tag: Tag,
        val parentId: Long,
        val index: Int,
    ) : TagListItem {
        override val key: Any get() = tag.id
        override val contentType: String get() = "child"
    }
}

private fun flattenTagItems(
    roots: List<Tag>,
    childrenByRoot: Map<Long, List<Tag>>,
    draggingParentId: Long?,
    draggingList: List<Tag>,
): List<TagListItem> = buildList {
    roots.forEach { root ->
        add(TagListItem.Header(root))
        val children = if (draggingParentId == root.id) {
            draggingList
        } else {
            childrenByRoot[root.id].orEmpty()
        }
        children.forEachIndexed { index, tag ->
            add(TagListItem.Child(tag, root.id, index))
        }
    }
}

/**
 * 「学业关联标签」管理全屏：根标签只读分区，子标签支持增/改/删与同分区长按拖拽排序。
 *
 * 内容为单个 [LazyColumn]（根头 + 子标签平铺），拖拽期间以本地 [TagDragState] 门控
 * 观察链重发射覆盖，结束后经 [TagManageViewModel.onReorder] 乐观提交并在失败时回滚。
 * 列表行不用 item placement 动画，避免懒加载回收时跟手势抢布局。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TagManageScreen(
    onBack: () -> Unit,
    viewModel: TagManageViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val itemHeightPx = with(LocalDensity.current) { rowHeight.roundToPx() }
    val latestChildren = rememberUpdatedState(uiState.childrenByRoot)
    val latestReorder = rememberUpdatedState(viewModel::onReorder)
    val dragState = remember(itemHeightPx) {
        TagDragState(
            itemHeightPx = itemHeightPx,
            currentChildren = { latestChildren.value },
            onReorder = { parentId, tags -> latestReorder.value(parentId, tags) },
        )
    }

    var addRoot by remember { mutableStateOf<Tag?>(null) }
    var addSubmitted by remember { mutableStateOf(false) }
    var showMergeSheet by remember { mutableStateOf(false) }

    val handleBack = remember(viewModel, onBack) {
        {
            viewModel.cancelDelete()
            viewModel.dismissRename()
            viewModel.clearError()
            onBack()
        }
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

    LaunchedEffect(uiState.busy, uiState.errorMessage) {
        if (addSubmitted && !uiState.busy) {
            if (uiState.errorMessage == null) addRoot = null
            addSubmitted = false
        }
    }

    Scaffold(
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
        if (uiState.loading) {
            Box(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                Text("加载中…", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            val listItems = remember(
                uiState.roots,
                uiState.childrenByRoot,
                dragState.parentId,
                dragState.list,
            ) {
                flattenTagItems(
                    uiState.roots,
                    uiState.childrenByRoot,
                    dragState.parentId,
                    dragState.list,
                )
            }
            val draggingItemId = dragState.itemId
            LazyColumn(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                items(
                    listItems,
                    key = { it.key },
                    contentType = { it.contentType },
                ) { item ->
                    when (item) {
                        is TagListItem.Header -> RootHeader(
                            root = item.root,
                            onAdd = {
                                addRoot = item.root
                                addSubmitted = false
                                viewModel.clearError()
                            },
                        )
                        is TagListItem.Child -> TagRow(
                            tagId = item.tag.id,
                            name = item.tag.name,
                            parentId = item.parentId,
                            index = item.index,
                            isDragging = draggingItemId == item.tag.id,
                            dragState = dragState,
                            onRename = { viewModel.requestRename(item.tag) },
                            onDelete = { viewModel.requestDelete(item.tag) },
                        )
                    }
                }
            }
        }
    }

    addRoot?.let { root ->
        AddTagDialog(
            root = root,
            errorMessage = uiState.errorMessage,
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

    uiState.renamingTag?.let { tag ->
        RenameDialog(
            tag = tag,
            errorMessage = uiState.errorMessage,
            onConfirm = { name -> viewModel.rename(tag.id, name) },
            onDismiss = { viewModel.dismissRename() },
        )
    }

    uiState.pendingDelete?.let { (tag, impact) ->
        DeleteConfirmDialog(
            tag = tag,
            impact = impact,
            onConfirm = { viewModel.confirmDelete() },
            onDismiss = { viewModel.cancelDelete() },
        )
    }

    if (showMergeSheet) {
        TagMergeSheet(
            uiState = uiState,
            onChoiceSelected = viewModel::setMergeChoice,
            onMerge = viewModel::confirmMerge,
            onRetryDetect = viewModel::detectMergeCandidates,
            onDismiss = { showMergeSheet = false },
        )
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

/** 子标签行：拖拽手柄 + 名称 + 改名/删除。参数拆成稳定类型，便于滑动时跳过重组。 */
@Composable
private fun TagRow(
    tagId: Long,
    name: String,
    parentId: Long,
    index: Int,
    isDragging: Boolean,
    dragState: TagDragState,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    val latestIndex = rememberUpdatedState(index)
    val latestRename = rememberUpdatedState(onRename)
    val latestDelete = rememberUpdatedState(onDelete)
    val onDragStart = remember(tagId, parentId, dragState) {
        {
            dragState.start(parentId, tagId, latestIndex.value, dragState.childrenOf(parentId))
        }
    }
    val onDrag = remember(tagId, dragState) {
        {
            amount: Float ->
            dragState.onDrag(amount, tagId)
        }
    }
    val onDragEnd = remember(dragState) { dragState::end }
    val onDragCancel = remember(dragState) { dragState::cancel }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(rowHeight)
            .graphicsLayer { alpha = if (isDragging) 0.72f else 1f }
            .padding(horizontal = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DragHandle(tagId, onDragStart, onDrag, onDragEnd, onDragCancel)
        Spacer(Modifier.width(16.dp))
        Text(name, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Icon(
            imageVector = Icons.Default.Edit,
            contentDescription = "改名",
            modifier = Modifier
                .size(40.dp)
                .clickable(onClick = { latestRename.value() })
                .padding(8.dp),
        )
        Icon(
            imageVector = Icons.Default.Delete,
            contentDescription = "删除",
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier
                .size(40.dp)
                .clickable(onClick = { latestDelete.value() })
                .padding(8.dp),
        )
    }
}

/** 长按后才占用纵向拖拽；超时前不 consume，避免挡住列表滑动。 */
@Composable
private fun DragHandle(
    tagId: Long,
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
        modifier = Modifier
            .size(40.dp)
            .pointerInput(tagId) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val slop = viewConfiguration.touchSlop
                    val downPos = down.position
                    val finishedBeforeLongPress = withTimeoutOrNull(
                        viewConfiguration.longPressTimeoutMillis,
                    ) {
                        waitUntilUpMovedOrCancel(down.id, downPos, slop)
                    }
                    if (finishedBeforeLongPress != null) return@awaitEachGesture
                    currentOnDragStart()
                    val dragged = drag(down.id) { change ->
                        change.consume()
                        currentOnDrag(change.positionChange().y)
                    }
                    if (dragged) currentOnDragEnd() else currentOnDragCancel()
                }
            }
            .padding(8.dp),
    )
}

private suspend fun AwaitPointerEventScope.waitUntilUpMovedOrCancel(
    pointerId: PointerId,
    downPos: Offset,
    slop: Float,
): Boolean {
    while (true) {
        val event = awaitPointerEvent()
        val change = event.changes.firstOrNull { it.id == pointerId } ?: return true
        if (change.changedToUpIgnoreConsumed()) return true
        if ((change.position - downPos).getDistance() > slop) return true
    }
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

/**
 * 拖拽会话。纵向像素偏移不进 Snapshot，避免每帧拖动把整页重组；
 * 只有跨行换位时才写 [list]。
 */
private class TagDragState(
    private val itemHeightPx: Int,
    private val currentChildren: () -> Map<Long, List<Tag>>,
    private val onReorder: (Long, List<Tag>) -> Unit,
) {
    var parentId by mutableStateOf<Long?>(null)
        private set
    var itemId by mutableStateOf<Long?>(null)
        private set
    var list by mutableStateOf<List<Tag>>(emptyList())
        private set
    private var startIndex = 0
    private var offsetY = 0f

    fun childrenOf(parentId: Long): List<Tag> {
        if (this.parentId == parentId && list.isNotEmpty()) return list
        return currentChildren()[parentId].orEmpty()
    }

    fun start(parentId: Long, itemId: Long, index: Int, children: List<Tag>) {
        this.parentId = parentId
        this.itemId = itemId
        list = children
        startIndex = index
        offsetY = 0f
    }

    fun onDrag(amount: Float, itemId: Long) {
        offsetY += amount
        val currentList = list
        if (currentList.isEmpty()) return
        val delta = reorderTargetIndex(currentList.size, itemHeightPx, offsetY)
        val target = (startIndex + delta).coerceIn(0, currentList.lastIndex)
        val current = currentList.indexOfFirst { it.id == itemId }
        if (current >= 0 && current != target) {
            list = moveItem(currentList, current, target)
        }
    }

    fun end() {
        val pid = parentId
        val dragged = list
        if (pid != null && dragged != currentChildren()[pid].orEmpty()) {
            onReorder(pid, dragged)
        }
        clear()
    }

    fun cancel() = clear()

    private fun clear() {
        parentId = null
        itemId = null
        list = emptyList()
        startIndex = 0
        offsetY = 0f
    }
}
