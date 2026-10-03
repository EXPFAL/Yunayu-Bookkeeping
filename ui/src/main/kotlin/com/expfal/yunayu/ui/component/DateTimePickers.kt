package com.expfal.yunayu.ui.component

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * 日期 + 时间选择流程：先选日期再选时间，确认后回调本地时区毫秒时间戳。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateTimePickerFlow(
    initialMillis: Long,
    onConfirm: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    val zone = ZoneId.systemDefault()
    val initialZoned = Instant.ofEpochMilli(initialMillis).atZone(zone)
    var step by remember { mutableStateOf(0) } // 0=date, 1=time
    var pickedDate by remember {
        mutableStateOf(initialZoned.toLocalDate())
    }

    if (step == 0) {
        val dateState = rememberDatePickerState(
            initialSelectedDateMillis = pickedDate
                .atStartOfDay(zone)
                .toInstant()
                .toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = onDismiss,
            confirmButton = {
                TextButton(
                    onClick = {
                        val millis = dateState.selectedDateMillis ?: return@TextButton
                        pickedDate = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()
                        step = 1
                    },
                ) { Text("下一步") }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) { Text("取消") }
            },
        ) {
            DatePicker(state = dateState)
        }
    } else {
        val timeState = rememberTimePickerState(
            initialHour = initialZoned.hour,
            initialMinute = initialZoned.minute,
            is24Hour = true,
        )
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("选择时间") },
            text = { TimePicker(state = timeState) },
            confirmButton = {
                TextButton(
                    onClick = {
                        val localTime = LocalTime.of(timeState.hour, timeState.minute)
                        val millis = pickedDate.atTime(localTime).atZone(zone).toInstant().toEpochMilli()
                        onConfirm(millis)
                    },
                ) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { step = 0 }) { Text("上一步") }
            },
        )
    }
}

/** 供测试或非 Compose 路径：把本地日期与时分合成毫秒。 */
fun combineLocalDateTimeMillis(
    date: LocalDate,
    hour: Int,
    minute: Int,
    zoneId: ZoneId = ZoneId.systemDefault(),
): Long = date.atTime(LocalTime.of(hour, minute)).atZone(zoneId).toInstant().toEpochMilli()
