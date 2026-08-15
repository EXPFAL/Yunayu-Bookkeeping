package com.expfal.yunayu.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.expfal.yunayu.domain.model.Tag

/**
 * 标签树折叠列表：按根标签分组展示，子类默认折叠，点击分组头展开/收起。
 *
 * 每个根分组依次渲染「分组头 → 根自身可点选行 →（展开时）子类行」，分组之间以分隔线隔开；
 * 展开状态在弹层生命周期内记忆（[rememberSaveable]），关闭即随组合销毁而重置。
 * 展开/收起前会记录当前滚动位置（首个可见项下标与偏移），并在子项增删后恢复，避免滚动跳变；
 * 外层列表默认限制最大高度 420dp，以稳定各宿主弹层尺寸、防止重测量。
 *
 * 本组件只负责把点击转换为 [onToggleSelect]，不关心关闭弹层等上层逻辑，因此可同时服务
 * 「单选（点选后由调用方关闭弹层）」与「多选（点选后不关闭）」两种场景。
 *
 * @param allTagsByRoot 根标签到其子标签的映射，键为根标签自身（根自身也参与点选）。
 * @param selectedIds 当前选中标签 id 集合，用于高亮行。
 * @param onToggleSelect 点击任意标签（根或子）时回调该标签 id。
 * @param modifier 作用于外层 [LazyColumn] 的修饰符。
 */
@Composable
fun TagTreeList(
    allTagsByRoot: Map<Tag, List<Tag>>,
    selectedIds: Set<Long>,
    onToggleSelect: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expandedRootIds by rememberSaveable { mutableStateOf(listOf<Long>()) }
    val listState = rememberLazyListState()
    var scrollAnchor by remember { mutableStateOf<Pair<Int, Int>?>(null) }

    LazyColumn(state = listState, modifier = modifier.fillMaxWidth().heightIn(max = 420.dp)) {
        allTagsByRoot.entries.forEachIndexed { index, (root, children) ->
            if (index > 0) {
                item(key = "divider-${root.id}") {
                    HorizontalDivider()
                }
            }
            item(key = "header-${root.id}") {
                TagGroupHeader(
                    root = root,
                    hasChildren = children.isNotEmpty(),
                    expanded = root.id in expandedRootIds,
                    onToggle = {
                        scrollAnchor = listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset
                        expandedRootIds = if (root.id in expandedRootIds) {
                            expandedRootIds - root.id
                        } else {
                            expandedRootIds + root.id
                        }
                    },
                )
            }
            item(key = "self-${root.id}") {
                TagRow(
                    tag = root,
                    selected = root.id in selectedIds,
                    onClick = { onToggleSelect(root.id) },
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
            if (root.id in expandedRootIds) {
                items(children, key = { it.id }) { child ->
                    TagRow(
                        tag = child,
                        selected = child.id in selectedIds,
                        onClick = { onToggleSelect(child.id) },
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(start = 32.dp),
                    )
                }
            }
        }
    }

    LaunchedEffect(expandedRootIds) {
        scrollAnchor?.let { (index, offset) ->
            listState.scrollToItem(index, offset)
        }
        scrollAnchor = null
    }
}

/** 分组头行：父类名加粗（字号高于子类、颜色用次强调色以区分层级），行尾展示展开/收起箭头，整行点击切换。 */
@Composable
private fun TagGroupHeader(
    root: Tag,
    hasChildren: Boolean,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = hasChildren, onClick = onToggle)
            .padding(horizontal = 24.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = root.name,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (hasChildren) {
            Icon(
                imageVector = if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                contentDescription = if (expanded) "收起" else "展开",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** 单个可点选标签行：选中态以主色文字 + Check 图标高亮。 */
@Composable
private fun TagRow(
    tag: Tag,
    selected: Boolean,
    onClick: () -> Unit,
    style: TextStyle,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = tag.name,
            modifier = Modifier.weight(1f),
            style = style,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        )
        if (selected) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = "已选",
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}
