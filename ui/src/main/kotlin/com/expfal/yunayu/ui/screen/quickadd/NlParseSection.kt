package com.expfal.yunayu.ui.screen.quickadd

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.expfal.yunayu.domain.model.Tag
import com.expfal.yunayu.domain.nl.model.NlParseFailure
import com.expfal.yunayu.domain.nl.model.NlTransactionDraft
import com.expfal.yunayu.ui.util.formatCents
import com.expfal.yunayu.ui.util.tagDisplayName
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** 将解析失败原因映射为可读中文提示。 */
fun nlFailureMessage(failure: NlParseFailure): String = when (failure) {
    NlParseFailure.EMPTY_INPUT -> "请先输入一句话，例如「午饭花了20块」"
    NlParseFailure.NO_AMOUNT -> "没听清金额，请补充金额后再试，例如「午饭20块」"
    NlParseFailure.MALFORMED_OUTPUT -> "没解析出来，换个说法再试一次"
    NlParseFailure.ENGINE_UNAVAILABLE -> "自动记暂不可用（未配置或网络异常），可手动记账"
}

/** 「手动记 / 自动记」输入模式切换控件。 */
@Composable
fun NlModeToggle(
    nlMode: Boolean,
    onModeChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterChip(
            selected = !nlMode,
            onClick = { onModeChange(false) },
            label = { Text("手动记") },
        )
        FilterChip(
            selected = nlMode,
            onClick = { onModeChange(true) },
            label = { Text("自动记") },
        )
    }
}

/** 自动记输入区：输入框 + 解析按钮 + 解析中态 / 失败文案 / 预览卡 + 确认按钮。 */
@Composable
fun NlParseSection(
    inputText: String,
    parsing: Boolean,
    saving: Boolean,
    draft: NlTransactionDraft?,
    failure: NlParseFailure?,
    nlTagId: Long?,
    suggestedTags: List<Tag>,
    rootNameById: Map<Long, String>,
    allTagsByRoot: Map<Tag, List<Tag>>,
    onInputChange: (String) -> Unit,
    onParse: () -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth()) {
        NlInputField(
            inputText = inputText,
            parsing = parsing,
            onInputChange = onInputChange,
            onParse = onParse,
        )
        when {
            parsing -> NlParsingIndicator()
            failure != null -> NlFailureText(failure)
            draft != null -> {
                NlDraftPreview(
                    draft = draft,
                    saving = saving,
                    tagDisplayName = resolveNlTagDisplayName(nlTagId, suggestedTags, allTagsByRoot, rootNameById),
                    onSave = onSave,
                )
            }
        }
    }
}

/** 自动记输入框与解析按钮。 */
@Composable
private fun NlInputField(
    inputText: String,
    parsing: Boolean,
    onInputChange: (String) -> Unit,
    onParse: () -> Unit,
) {
    OutlinedTextField(
        value = inputText,
        onValueChange = onInputChange,
        label = { Text("用一句话记账") },
        placeholder = { Text("例如：午饭花了20块") },
        minLines = 1,
        maxLines = 3,
        modifier = Modifier.fillMaxWidth().imePadding(),
    )
    Spacer(Modifier.height(12.dp))
    Button(
        onClick = onParse,
        enabled = !parsing && inputText.isNotBlank(),
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp),
    ) {
        Text(if (parsing) "解析中…" else "解析")
    }
}

/** 解析中的占位提示。 */
@Composable
private fun NlParsingIndicator() {
    Text(
        text = "正在解析，请稍候…",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
    )
}

/** 解析失败文案。 */
@Composable
private fun NlFailureText(failure: NlParseFailure) {
    Text(
        text = nlFailureMessage(failure),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.error,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp),
    )
}

/** 解析结果预览卡与确认保存按钮。 */
@Composable
private fun NlDraftPreview(
    draft: NlTransactionDraft,
    saving: Boolean,
    tagDisplayName: String?,
    onSave: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text(
                text = "¥ " + formatCents(draft.amountCents),
                style = MaterialTheme.typography.headlineMedium,
            )
            Spacer(Modifier.height(12.dp))
            PreviewRow(label = "分类", value = tagDisplayName ?: draft.tagPhrase ?: "未分类")
            draft.note?.takeIf { it.isNotBlank() }?.let { PreviewRow(label = "备注", value = it) }
            PreviewRow(label = "时间", value = formatNlTime(draft.occurredAtEpochMillis))
        }
    }
    if (draft.tagId == null && !draft.tagPhrase.isNullOrBlank()) {
        Text(
            text = "未匹配到分类，将记为未分类",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
        )
    }
    Spacer(Modifier.height(16.dp))
    Button(
        onClick = onSave,
        enabled = !saving,
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp),
    ) {
        Text(if (saving) "记一笔中…" else "确认记账")
    }
}

/** 依 nlTagId 解析标签展示名：依次在建议 chips 与全量标签（根/子）中查找，缺失返回 null。 */
private fun resolveNlTagDisplayName(
    tagId: Long?,
    suggestedTags: List<Tag>,
    allTagsByRoot: Map<Tag, List<Tag>>,
    rootNameById: Map<Long, String>,
): String? {
    if (tagId == null) return null
    val tag = suggestedTags.firstOrNull { it.id == tagId }
        ?: allTagsByRoot.keys.firstOrNull { it.id == tagId }
        ?: allTagsByRoot.values.flatten().firstOrNull { it.id == tagId }
        ?: return null
    return tagDisplayName(tag, rootNameById)
}

/** 预览卡中的单行「标签：值」信息。 */
@Composable
private fun PreviewRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

/** 毫秒 → 「MM-dd HH:mm」本地时间文本（固定 Locale.US 保证跨设备一致）。 */
private fun formatNlTime(millis: Long): String =
    Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).format(NL_TIME_FORMAT)

private val NL_TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("MM-dd HH:mm", Locale.US)
