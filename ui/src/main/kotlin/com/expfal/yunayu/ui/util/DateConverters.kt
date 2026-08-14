package com.expfal.yunayu.ui.util

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * M3 [androidx.compose.material3.DatePickerState] 契约以 UTC 毫秒表达日期。
 *
 * DatePicker 的 selectedDateMillis 恒为 UTC 零点毫秒，与设备时区无关。若用
 * [java.time.ZoneId.systemDefault] 零点换算，在 UTC+8 等东八区会把预填日期前移一天，
 * 因此 SemesterSetupSheet 与 DatePicker 之间的毫秒 ↔ LocalDate 往返统一走 UTC。
 */
internal fun LocalDate.toUtcEpochMillis(): Long =
    atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

/** UTC 毫秒 → LocalDate（与 [toUtcEpochMillis] 互为逆操作）。 */
internal fun Long.toUtcLocalDate(): LocalDate =
    Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()
