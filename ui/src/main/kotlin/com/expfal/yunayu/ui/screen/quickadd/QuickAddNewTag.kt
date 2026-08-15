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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.expfal.yunayu.domain.model.Tag

/** 「新建标签」表单：输入名 + 根类选择 + AI 推荐所属根类 + 创建按钮。 */
@Composable
fun QuickAddNewTag(
    newTagName: String,
    newTagRootId: Long?,
    newTagBusy: Boolean,
    newTagError: String?,
    rootSuggesting: Boolean,
    rootTags: List<Tag>,
    onNameChange: (String) -> Unit,
    onRootSelect: (Long) -> Unit,
    onSuggestRoot: () -> Unit,
    onCreate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
            .padding(bottom = 28.dp),
    ) {
        OutlinedTextField(
            value = newTagName,
            onValueChange = onNameChange,
            label = { Text("新标签名") },
            placeholder = { Text("例如：咖啡") },
            singleLine = true,
            enabled = !newTagBusy,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = "所属根类",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        RootChips(
            rootTags = rootTags,
            selectedRootId = newTagRootId,
            enabled = !newTagBusy,
            onRootSelect = onRootSelect,
        )
        Spacer(Modifier.height(8.dp))
        TextButton(
            onClick = onSuggestRoot,
            enabled = newTagName.isNotBlank() && !rootSuggesting && !newTagBusy,
        ) {
            Text(if (rootSuggesting) "正在推荐…" else "AI 推荐所属根类")
        }
        if (newTagError != null) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = newTagError,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = onCreate,
            enabled = newTagName.isNotBlank() && newTagRootId != null && !newTagBusy,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
        ) {
            Text(if (newTagBusy) "创建中…" else "创建")
        }
    }
}

/** 根类 FilterChip 选择组，横向滚动避免根类过多时溢出。 */
@Composable
private fun RootChips(
    rootTags: List<Tag>,
    selectedRootId: Long?,
    enabled: Boolean,
    onRootSelect: (Long) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        rootTags.forEach { root ->
            FilterChip(
                selected = selectedRootId == root.id,
                onClick = { onRootSelect(root.id) },
                enabled = enabled,
                label = { Text(root.name) },
            )
        }
    }
}
