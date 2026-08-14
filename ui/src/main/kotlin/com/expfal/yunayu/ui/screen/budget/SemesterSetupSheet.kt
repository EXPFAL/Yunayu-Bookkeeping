package com.expfal.yunayu.ui.screen.budget

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.expfal.yunayu.domain.model.DateRange
import com.expfal.yunayu.ui.util.filterBudgetInput
import com.expfal.yunayu.ui.util.parseBudgetToCents
import com.expfal.yunayu.ui.util.toUtcEpochMillis
import com.expfal.yunayu.ui.util.toUtcLocalDate
import com.expfal.yunayu.ui.util.vibrateSuccess
import java.time.LocalDate
import java.util.Locale

/**
 * 学期设置底部弹层：名称 + 起止日期（只读，点击弹 DatePicker）+ 总预算（小数过滤）+
 * 考试周 / 寒暑假区间配置。
 *
 * 编辑模式从 [BudgetViewModel.uiState] 的当前学期预填（含区间，区间以 UI 为唯一来源）；
 * 保存成功经 [BudgetEvent.Saved] 触发震动并关闭，失败经 [BudgetEvent.SaveFailed] 显示
 * 温和错误文案。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SemesterSetupSheet(
    viewModel: BudgetViewModel,
    onDismissRequest: () -> Unit,
) {
    val initialSemester = viewModel.uiState.value.semester
    var name by remember { mutableStateOf(initialSemester?.name ?: "") }
    var startDate by remember { mutableStateOf(initialSemester?.startDate ?: LocalDate.now()) }
    var endDate by remember { mutableStateOf(initialSemester?.endDate ?: LocalDate.now().plusMonths(4)) }
    var budgetText by remember { mutableStateOf(initialSemester?.let { centsToBudgetText(it.totalBudgetCents) } ?: "") }
    var examWeekRanges by remember { mutableStateOf(initialSemester?.examWeekRanges ?: emptyList()) }
    var vacationRanges by remember { mutableStateOf(initialSemester?.vacationRanges ?: emptyList()) }
    var showStartPicker by remember { mutableStateOf(false) }
    var showEndPicker by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var saveFailed by remember { mutableStateOf(false) }
    val context = LocalContext.current

    LaunchedEffect(viewModel, context) {
        viewModel.events.collect { event ->
            when (event) {
                BudgetEvent.Saved -> {
                    context.vibrateSuccess()
                    onDismissRequest()
                }
                BudgetEvent.SaveFailed -> {
                    saving = false
                    saveFailed = true
                }
            }
        }
    }

    val budgetCents = parseBudgetToCents(budgetText)
    val validationError = when {
        name.isBlank() -> "给学期起个名字吧"
        startDate.isAfter(endDate) -> "结束日期要晚于开始日期"
        budgetCents == null -> "预算金额要大于 0"
        else -> null
    }

    ModalBottomSheet(onDismissRequest = { if (!saving) onDismissRequest() }) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 28.dp),
        ) {
            Text(
                if (initialSemester == null) "设置学期预算" else "编辑学期预算",
                style = MaterialTheme.typography.titleLarge,
            )
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("学期名称") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            ReadOnlyDateField(
                value = startDate.toDisplayText(),
                label = "开始日期",
                onClick = { showStartPicker = true },
            )
            Spacer(Modifier.height(12.dp))
            ReadOnlyDateField(
                value = endDate.toDisplayText(),
                label = "结束日期",
                onClick = { showEndPicker = true },
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = budgetText,
                onValueChange = { budgetText = filterBudgetInput(it) },
                label = { Text("总预算（元）") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(16.dp))
            RangeSection(
                title = "考试周",
                ranges = examWeekRanges,
                onAdd = { examWeekRanges = examWeekRanges + it },
                onRemove = { index -> examWeekRanges = examWeekRanges.filterIndexed { i, _ -> i != index } },
            )
            Spacer(Modifier.height(8.dp))
            RangeSection(
                title = "寒暑假",
                ranges = vacationRanges,
                onAdd = { vacationRanges = vacationRanges + it },
                onRemove = { index -> vacationRanges = vacationRanges.filterIndexed { i, _ -> i != index } },
            )
            val errorText = validationError ?: if (saveFailed) "刚才没保存上，再试一次" else null
            if (errorText != null) {
                Spacer(Modifier.height(12.dp))
                Text(
                    errorText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Spacer(Modifier.height(20.dp))
            Button(
                onClick = {
                    if (validationError == null && budgetCents != null) {
                        saving = true
                        saveFailed = false
                        viewModel.saveSemester(
                            name.trim(),
                            startDate,
                            endDate,
                            budgetCents,
                            examWeekRanges,
                            vacationRanges,
                        )
                    }
                },
                enabled = !saving,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
            ) {
                Text(if (saving) "保存中…" else "保存")
            }
        }
    }

    if (showStartPicker) {
        DatePickerSheet(
            initialDate = startDate,
            onConfirm = {
                startDate = it
                showStartPicker = false
            },
            onDismiss = { showStartPicker = false },
        )
    }
    if (showEndPicker) {
        DatePickerSheet(
            initialDate = endDate,
            onConfirm = {
                endDate = it
                showEndPicker = false
            },
            onDismiss = { showEndPicker = false },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DatePickerSheet(
    initialDate: LocalDate,
    onConfirm: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
) {
    val state = rememberDatePickerState(initialSelectedDateMillis = initialDate.toUtcEpochMillis())
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { state.selectedDateMillis?.let { onConfirm(it.toUtcLocalDate()) } ?: onDismiss() }) {
                Text("确定")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    ) {
        DatePicker(state = state)
    }
}

/** 只读日期输入框：透传点击到 [onClick] 以弹出日期选择器。 */
@Composable
private fun ReadOnlyDateField(
    value: String,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            modifier = Modifier.fillMaxWidth(),
        )
        Box(Modifier.matchParentSize().clickable(onClick = onClick))
    }
}

/** 考试周 / 寒暑假区间编辑区块：可折叠，展示已有区间并可新增、删除。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RangeSection(
    title: String,
    ranges: List<DateRange>,
    onAdd: (DateRange) -> Unit,
    onRemove: (Int) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    var draftStart by remember { mutableStateOf<LocalDate?>(null) }
    var draftEnd by remember { mutableStateOf<LocalDate?>(null) }
    var showStartPicker by remember { mutableStateOf(false) }
    var showEndPicker by remember { mutableStateOf(false) }
    var rangeError by remember { mutableStateOf<String?>(null) }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded },
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.weight(1f),
        )
        Icon(
            imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
            contentDescription = if (expanded) "收起" else "展开",
        )
    }

    if (expanded) {
        Spacer(Modifier.height(8.dp))
        ranges.forEachIndexed { index, range ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${range.start.toDisplayText()} ~ ${range.endInclusive.toDisplayText()}",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { onRemove(index) }) {
                    Icon(Icons.Default.Delete, contentDescription = "删除区间")
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            ReadOnlyDateField(
                value = draftStart?.toDisplayText() ?: "开始",
                label = "开始",
                onClick = { showStartPicker = true },
                modifier = Modifier.weight(1f),
            )
            Text(
                "~",
                Modifier.padding(horizontal = 8.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            ReadOnlyDateField(
                value = draftEnd?.toDisplayText() ?: "结束",
                label = "结束",
                onClick = { showEndPicker = true },
                modifier = Modifier.weight(1f),
            )
        }
        Row {
            Spacer(Modifier.weight(1f))
            TextButton(
                onClick = {
                    val start = draftStart
                    val end = draftEnd
                    when {
                        start == null || end == null -> rangeError = "请选择起止日期"
                        start.isAfter(end) -> rangeError = "开始日期要早于结束日期"
                        else -> {
                            onAdd(DateRange(start, end))
                            draftStart = null
                            draftEnd = null
                            rangeError = null
                        }
                    }
                },
            ) {
                Text("添加区间")
            }
        }
        rangeError?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }

    if (showStartPicker) {
        DatePickerSheet(
            initialDate = draftStart ?: LocalDate.now(),
            onConfirm = {
                draftStart = it
                showStartPicker = false
            },
            onDismiss = { showStartPicker = false },
        )
    }
    if (showEndPicker) {
        DatePickerSheet(
            initialDate = draftEnd ?: LocalDate.now(),
            onConfirm = {
                draftEnd = it
                showEndPicker = false
            },
            onDismiss = { showEndPicker = false },
        )
    }
}

/** 分 → 预算输入文本：整数元省略小数，否则保留两位。 */
private fun centsToBudgetText(cents: Long): String =
    if (cents % 100L == 0L) (cents / 100L).toString() else String.format(Locale.US, "%.2f", cents / 100.0)

private fun LocalDate.toDisplayText(): String =
    String.format(Locale.US, "%04d-%02d-%02d", year, monthValue, dayOfMonth)
