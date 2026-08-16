package com.expfal.yunayu.ui.screen.tagmanage

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.expfal.yunayu.domain.model.MergeCandidate

/**
 * 「标签整合」底部弹层：展示疑似重复标签对，供用户逐对选择整合方向并确认合并。
 *
 * 检测中显示 loading，候选为空按 [TagManageUiState.mergeDetectFailed] 区分「检测不可用」与
 * 「未发现重复」；候选列表每对提供三选（A 并入 B / B 并入 A / 各自保留）与单对「合并」按钮。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TagMergeSheet(
    uiState: TagManageUiState,
    onChoiceSelected: (MergeCandidate, MergeChoice) -> Unit,
    onMerge: (MergeCandidate) -> Unit,
    onRetryDetect: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth()) {
            Text(
                text = "标签整合",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "检测语义重复的标签对；「→」左侧并入右侧（保留右侧标签）",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
            Spacer(Modifier.height(8.dp))
            when {
                uiState.mergeDetecting -> MergeDetectingContent()
                uiState.mergeCandidates.isEmpty() -> MergeEmptyContent(
                    message = if (uiState.mergeDetectFailed) {
                        "未配置 API，检测不可用"
                    } else {
                        "未发现疑似重复标签"
                    },
                    onRetryDetect = onRetryDetect,
                )
                else -> MergeCandidateList(
                    candidates = uiState.mergeCandidates,
                    choices = uiState.mergeChoices,
                    merging = uiState.merging,
                    onChoiceSelected = onChoiceSelected,
                    onMerge = onMerge,
                )
            }
        }
    }
}

/** 检测进行中占位。 */
@Composable
private fun MergeDetectingContent() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator()
        Spacer(Modifier.height(12.dp))
        Text("正在检测疑似重复标签…", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** 候选为空态：区分「检测不可用」与「未发现重复」，均提供重新检测入口。 */
@Composable
private fun MergeEmptyContent(message: String, onRetryDetect: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onRetryDetect) { Text("重新检测") }
    }
}

/** 候选对列表。 */
@Composable
private fun MergeCandidateList(
    candidates: List<MergeCandidate>,
    choices: Map<String, MergeChoice>,
    merging: Boolean,
    onChoiceSelected: (MergeCandidate, MergeChoice) -> Unit,
    onMerge: (MergeCandidate) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        items(candidates, key = { "${it.tagA.id}:${it.tagB.id}" }) { candidate ->
            MergeCandidateRow(
                candidate = candidate,
                choice = choices["${candidate.tagA.id}:${candidate.tagB.id}"] ?: MergeChoice.KEEP_BOTH,
                merging = merging,
                onChoiceSelected = { onChoiceSelected(candidate, it) },
                onMerge = { onMerge(candidate) },
            )
            HorizontalDivider()
        }
    }
}

/** 单个候选对：名称 + 记录数 + 三选方向 + 合并按钮。 */
@Composable
private fun MergeCandidateRow(
    candidate: MergeCandidate,
    choice: MergeChoice,
    merging: Boolean,
    onChoiceSelected: (MergeChoice) -> Unit,
    onMerge: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text(
            text = "${candidate.tagA.name}（${candidate.countA} 条） vs " +
                "${candidate.tagB.name}（${candidate.countB} 条）",
            style = MaterialTheme.typography.bodyLarge,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = "合并后共 ${candidate.countA + candidate.countB} 条",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = choice == MergeChoice.A_INTO_B,
                onClick = { onChoiceSelected(MergeChoice.A_INTO_B) },
                label = { Text("${candidate.tagA.name} → ${candidate.tagB.name}") },
            )
            FilterChip(
                selected = choice == MergeChoice.B_INTO_A,
                onClick = { onChoiceSelected(MergeChoice.B_INTO_A) },
                label = { Text("${candidate.tagB.name} → ${candidate.tagA.name}") },
            )
            FilterChip(
                selected = choice == MergeChoice.KEEP_BOTH,
                onClick = { onChoiceSelected(MergeChoice.KEEP_BOTH) },
                label = { Text("各自保留") },
            )
        }
        Spacer(Modifier.height(4.dp))
        TextButton(
            onClick = onMerge,
            enabled = choice != MergeChoice.KEEP_BOTH && !merging,
        ) {
            Text("合并")
        }
    }
}
