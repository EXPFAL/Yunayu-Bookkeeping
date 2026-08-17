package com.expfal.yunayu.ui.util

import java.util.Locale

/** 预算金额整数部分最大位数（9 位，与 QuickAdd 的 7 位口径区分）。 */
internal const val MAX_BUDGET_INTEGER_DIGITS = 9

/**
 * 预算输入过滤：仅保留 ASCII 数字与一个小数点，整数 ≤9 位、小数 ≤2 位，前导小数点补 0。
 *
 * 必须用 `it in '0'..'9'` 而非 [Char.isDigit]：isDigit 会把阿拉伯-印度数字等 Unicode
 * 数字（如 '٣'）当作合法数字，进而在小数位减法解析中产生错误金额。
 */
internal fun filterBudgetInput(raw: String): String {
    val filtered = raw.filter { it in '0'..'9' || it == '.' }
    val dotIndex = filtered.indexOf('.')
    if (dotIndex < 0) return filtered.take(MAX_BUDGET_INTEGER_DIGITS)
    var integer = filtered.substring(0, dotIndex).take(MAX_BUDGET_INTEGER_DIGITS)
    if (integer.isEmpty()) integer = "0"
    val fraction = filtered.substring(dotIndex + 1).filter { it in '0'..'9' }.take(2)
    return "$integer.$fraction"
}

/**
 * 将预算文本解析为「分」；空串、非法文本或结果 ≤0 均返回 null。
 *
 * 仅接受 ASCII 数字与至多一个小数点，小数位 ≤2；整数部分位数上限由 [filterBudgetInput]
 * 保证，此处仅做溢出兜底。
 */
internal fun parseBudgetToCents(text: String): Long? {
    val trimmed = text.trim()
    if (trimmed.isEmpty()) return null
    if (trimmed.count { it == '.' } > 1) return null
    if (!trimmed.all { it in '0'..'9' || it == '.' }) return null
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

/**
 * 将期初余额文本解析为「分」；空串视为 0（未设置期初），非法文本返回 null。
 *
 * 校验规则与 [parseBudgetToCents] 一致（仅 ASCII 数字与至多一个小数点、小数位 ≤2、
 * 溢出兜底），唯一差异是允许结果为 0：期初余额可为 0（表示未设置/清零），且负数因
 * 不满足字符白名单而被拒绝。
 */
internal fun parseInitialBalanceToCents(text: String): Long? {
    val trimmed = text.trim()
    if (trimmed.isEmpty()) return 0L
    if (trimmed.count { it == '.' } > 1) return null
    if (!trimmed.all { it in '0'..'9' || it == '.' }) return null
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
    return if (total >= 0L) total else null
}

/**
 * 分 → 期初余额输入文本：0 返回空串（未设置），整数元省略小数，否则保留两位。
 *
 * 输出不含千分位逗号，保证能被 [parseInitialBalanceToCents] 直接回读。
 */
internal fun centsToInitialBalanceText(cents: Long): String =
    if (cents <= 0L) {
        ""
    } else if (cents % 100L == 0L) {
        (cents / 100L).toString()
    } else {
        String.format(Locale.US, "%.2f", cents / 100.0)
    }
