package com.expfal.yunayu.domain.nl

import java.time.LocalDate
import java.time.ZoneId

/** 自然语言记账的纯函数工具：金额与相对日期解析。 */
object NlAmountDate {

    /**
     * 将金额文本解析为「分」。仅接受数字与至多一个小数点，小数位 ≤2；
     * 空串、非法文本、溢出或结果 ≤0 均返回 `null`。
     */
    fun parseAmountToCents(text: String): Long? {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return null
        if (trimmed.count { it == '.' } > 1) return null
        if (!trimmed.all { it in '0'..'9' || it == '.' }) return null

        val parts = trimmed.split('.')
        val integer = parts[0]
        val fraction = parts.getOrNull(1) ?: ""
        if (integer.isEmpty()) return null
        if (fraction.length > MAX_FRACTION_DIGITS) return null

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

    /**
     * 把日期文本结合基准时间 [nowEpochMillis] 折算为 epoch 毫秒。
     *
     * 「今天」返回基准时间，「昨天/前天」按 24/48 小时向前偏移；「YYYY-MM-DD」按系统
     * 默认时区当日零点折算；其余（含空串、非法日期）返回 `null`。
     */
    fun parseOccurredAtEpochMillis(dateText: String?, nowEpochMillis: Long): Long? {
        val trimmed = dateText?.trim().orEmpty()
        if (trimmed.isEmpty()) return null
        return when (trimmed) {
            "今天" -> nowEpochMillis
            "昨天" -> nowEpochMillis - DAY_MILLIS
            "前天" -> nowEpochMillis - 2 * DAY_MILLIS
            else -> runCatching {
                LocalDate.parse(trimmed)
                    .atStartOfDay(ZoneId.systemDefault())
                    .toInstant()
                    .toEpochMilli()
            }.getOrNull()
        }
    }

    /** 小数部分最多位数。 */
    private const val MAX_FRACTION_DIGITS = 2

    /** 一天毫秒数。 */
    private const val DAY_MILLIS = 24L * 60 * 60 * 1000
}
