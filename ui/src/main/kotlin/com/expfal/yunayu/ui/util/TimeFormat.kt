package com.expfal.yunayu.ui.util

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * 将毫秒时间戳格式化为「MM-dd HH:mm」本地时间文本（固定 [Locale.US] 保证跨设备一致）。
 *
 * 上提为公共函数供最近记录行与后续收支管理页等场景统一复用，避免重复格式化逻辑。
 */
fun formatTime(millis: Long): String =
    Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).format(TIME_FORMAT)

/** 将毫秒时间戳格式化为「yyyy-MM-dd」本地日期。 */
fun formatDate(millis: Long): String =
    Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).format(DATE_FORMAT)

/** 解析「yyyy-MM-dd」为当天 0 点毫秒时间戳；格式非法时返回 null。 */
fun parseDateToStartOfDayMillis(text: String, zoneId: ZoneId = ZoneId.systemDefault()): Long? =
    runCatching {
        LocalDate.parse(text.trim(), DATE_FORMAT)
            .atStartOfDay(zoneId)
            .toInstant()
            .toEpochMilli()
    }.getOrNull()

private val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("MM-dd HH:mm", Locale.US)
private val DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.US)
