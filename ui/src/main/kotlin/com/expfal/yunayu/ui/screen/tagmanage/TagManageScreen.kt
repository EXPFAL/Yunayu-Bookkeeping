package com.expfal.yunayu.ui.screen.tagmanage

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.foundation.lazy.itemsIndexed
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

/** 标签行固定高度，拖拽换算目标索引与 [Modifier.height] 使用同一常量，保证行高口径一致。 */
private val rowHeight = 56.dp

/**
 * 「学业关联标签」管理全屏：根标签只读分区，子标签支持增/改/删与同分区长按拖拽排序。
 *
 * 内容为单个 [LazyColumn]（根头 + 子标签平铺），拖拽期间以本地 [dragList] 门控
 * 观察链重发射覆盖，结束后经 [TagManageViewModel.onReorder] 乐观提交并在失败时回滚。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun TagManageScreen(
    onBack: () -> Unit,
    viewModel: TagManageViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val itemHeightPx = with(LocalDensity.current) { rowHeight.roundToPx() }

    var draggingParentId by remember { mutableStateOf<Long?>(null) }
    var draggingItemId by remember { mutableStateOf<Long?>(null) }
    var dragList by remember { mutableStateOf<List<Tag>>(emptyList()) }
    var dragStartIndex by remember { mutableStateOf(0) }
    var dragOffsetY by remember { mutableStateOf(0f) }

    var addRoot by remember { mutableStateOf<Tag?>(null) }
    var addSubmitted by remember { mutableStateOf(false) }

    BackHandler(onBack = onBack)

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            if (event is TagManageEvent.Failed) snackbarHostState.showSnackbar("操作失败，请重试")
        }
    }

    LaunchedEffect(uiState.busy, uiState.errorMessage) {
        if (addSubmitted && !uiState.busy) {
            if (uiState.errorMessage == null) addRoot = null
            addSubmitted = false
        }
    }

    fun clearDrag() {
        draggingParentId = null
        draggingItemId = null
        dragList = emptyList()
        dragStartIndex = 0
        dragOffsetY = 0f
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("标签管理") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
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
            LazyColumn(Modifier.fillMaxSize().padding(innerPadding)) {
                uiState.roots.forEach { root ->
                    item(key = "header-${root.id}") {
                        RootHeader(
                            root,
                            onAdd = {
                                addRoot = root
                                addSubmitted = false
                            },
                        )
                    }
                    val children = if (draggingParentId == root.id) {
                        dragList
                    } else {
                        uiState.childrenByRoot[root.id].orEmpty()
                    }
                    itemsIndexed(children, key = { _, tag -> tag.id }) { index, tag ->
                        val isDragging = draggingItemId == tag.id
                        TagRow(
                            tag = tag,
                            isDragging = isDragging,
                            modifier = if (draggingParentId == root.id) Modifier else Modifier.animateItemPlacement(),
                            onDragStart = {
                                draggingParentId = root.id
                                draggingItemId = tag.id
                                dragList = children
                                dragStartIndex = index
                                dragOffsetY = 0f
                            },
                            onDrag = { amount ->
                                dragOffsetY += amount
                                val delta = reorderTargetIndex(dragList.size, itemHeightPx, dragOffsetY)
                                val target = (dragStartIndex + delta).coerceIn(0, dragList.lastIndex)
                                val current = dragList.indexOfFirst { it.id == tag.id }
                                if (current != target) dragList = moveItem(dragList, current, target)
                            },
                            onDragEnd = {
                                viewModel.onReorder(root.id, dragList)
                                clearDrag()
                            },
                            onDragCancel = { clearDrag() },
                            onRename = { viewModel.requestRename(tag) },
                            onDelete = { viewModel.requestDelete(tag) },
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
    modifier: Modifier = Modifier,
    onDragStart: () -> Unit,
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(rowHeight)
            .background(if (isDragging) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface)
            .padding(horizontal = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DragHandle(tag, onDragStart, onDrag, onDragEnd, onDragCancel)
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
        modifier = Modifier.pointerInput(tag.id) {
            detectDragGesturesAfterLongPress(
                onDragStart = { currentOnDragStart() },
                onDrag = { change, dragAmount ->
                    change.consume()
                    currentOnDrag(dragAmount.y)
                },
                onDragEnd = { currentOnDragEnd() },
                onDragCancel = { currentOnDragCancel() },
            )
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
    val message = buildString {
        if (childrenCount > 0) {
            append("删除「${tag.name}」会同时删除 $childrenCount 个子标签，")
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
