package com.expfal.yunayu.domain.nl

import com.expfal.yunayu.domain.util.MoneyAmount
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** 自然语言记账的纯函数工具：金额与相对日期解析。 */
object NlAmountDate {

    /**
     * 将金额文本解析为「分」。仅接受数字与至多一个小数点，小数位 ≤2；
     * 空串、非法文本、溢出或结果 ≤0 均返回 `null`。
     */
    fun parseAmountToCents(text: String): Long? = MoneyAmount.parseToCents(text, allowZero = false)

    /**
     * 把日期文本结合基准时间 [nowEpochMillis] 折算为 epoch 毫秒。
     *
     * 「今天」返回基准时间；「昨天/前天」按系统默认时区的日历日回退（当日零点），
     * 避免按 24h 墙钟偏移导致跨日错误；「YYYY-MM-DD」按系统默认时区当日零点折算；
     * 其余（含空串、非法日期）返回 `null`。
     */
    fun parseOccurredAtEpochMillis(dateText: String?, nowEpochMillis: Long): Long? {
        val trimmed = dateText?.trim().orEmpty()
        if (trimmed.isEmpty()) return null
        val zone = ZoneId.systemDefault()
        return when (trimmed) {
            "今天" -> nowEpochMillis
            "昨天" -> localDateOf(nowEpochMillis, zone).minusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
            "前天" -> localDateOf(nowEpochMillis, zone).minusDays(2).atStartOfDay(zone).toInstant().toEpochMilli()
            else -> runCatching {
                LocalDate.parse(trimmed)
                    .atStartOfDay(zone)
                    .toInstant()
                    .toEpochMilli()
            }.getOrNull()
        }
    }

    private fun localDateOf(epochMillis: Long, zone: ZoneId): LocalDate =
        Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalDate()
}
