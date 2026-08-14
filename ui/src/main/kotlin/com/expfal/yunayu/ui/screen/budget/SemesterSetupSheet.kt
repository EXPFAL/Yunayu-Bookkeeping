package com.expfal.yunayu.ui.screen.budget

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.expfal.yunayu.ui.util.vibrateSuccess
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

/**
 * 学期设置底部弹层：名称 + 起止日期（只读，点击弹 DatePicker）+ 总预算（小数过滤）。
 *
 * 编辑模式从 [BudgetViewModel.uiState] 的当前学期预填；保存成功经 [BudgetEvent.Saved]
 * 触发震动并关闭，失败经 [BudgetEvent.SaveFailed] 显示温和错误文案。
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
                        viewModel.saveSemester(name.trim(), startDate, endDate, budgetCents)
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
    val state = rememberDatePickerState(initialSelectedDateMillis = initialDate.toEpochMillis())
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { state.selectedDateMillis?.let { onConfirm(it.toLocalDate()) } ?: onDismiss() }) {
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

/** 预算输入过滤：仅数字与一个小数点，整数 ≤9 位、小数 ≤2 位，前导小数点补 0。 */
private fun filterBudgetInput(raw: String): String {
    val filtered = raw.filter { it.isDigit() || it == '.' }
    val dotIndex = filtered.indexOf('.')
    if (dotIndex < 0) return filtered.take(MAX_INTEGER_DIGITS)
    var integer = filtered.substring(0, dotIndex).take(MAX_INTEGER_DIGITS)
    if (integer.isEmpty()) integer = "0"
    val fraction = filtered.substring(dotIndex + 1).filter { it.isDigit() }.take(2)
    return "$integer.$fraction"
}

/** 将预算文本解析为「分」；空串、非法文本或结果 ≤0 均返回 null。 */
private fun parseBudgetToCents(text: String): Long? {
    val trimmed = text.trim()
    if (trimmed.isEmpty()) return null
    if (trimmed.count { it == '.' } > 1) return null
    if (!trimmed.all { it.isDigit() || it == '.' }) return null
    val parts = trimmed.split('.')
    val integer = parts[0]
    val fraction = parts.getOrNull(1) ?: ""
    if (integer.isEmpty()) return null
    if (fraction.length > 2) return null
    val yuan = integer.toLongOrNull() ?: return null
    if (yuan > Long.MAX_VALUE / 100L) return null
    val cents = when (fraction.length) {
        0 -> 0L
        1 -> (fraction[0] - '0') * 10L
        else -> (fraction[0] - '0') * 10L + (fraction[1] - '0')
    }
    val total = yuan * 100L + cents
    return if (total > 0L) total else null
}

/** 分 → 预算输入文本：整数元省略小数，否则保留两位。 */
private fun centsToBudgetText(cents: Long): String =
    if (cents % 100L == 0L) (cents / 100L).toString() else String.format(Locale.US, "%.2f", cents / 100.0)

private fun LocalDate.toDisplayText(): String =
    String.format(Locale.US, "%04d-%02d-%02d", year, monthValue, dayOfMonth)

private fun LocalDate.toEpochMillis(): Long =
    atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

private fun Long.toLocalDate(): LocalDate =
    Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()).toLocalDate()

private const val MAX_INTEGER_DIGITS = 9
