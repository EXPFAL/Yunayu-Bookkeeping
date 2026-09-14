package com.expfal.yunayu.ui.util

import com.expfal.yunayu.domain.util.MoneyAmount
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
 */
internal fun parseBudgetToCents(text: String): Long? =
    MoneyAmount.parseToCents(text, allowZero = false)

/**
 * 将期初余额文本解析为「分」；空串视为 0（未设置期初），非法文本返回 null。
 */
internal fun parseInitialBalanceToCents(text: String): Long? =
    MoneyAmount.parseToCents(text, allowZero = true)

/**
 * 分 → 期初余额 / 预算输入文本：≤0 返回空串，整数元省略小数，否则保留两位。
 *
 * 输出不含千分位逗号，保证能被 [parseInitialBalanceToCents] / [parseBudgetToCents] 直接回读。
 */
internal fun centsToInitialBalanceText(cents: Long): String =
    if (cents <= 0L) {
        ""
    } else if (cents % 100L == 0L) {
        (cents / 100L).toString()
    } else {
        String.format(Locale.US, "%.2f", cents / 100.0)
    }

/** 分 → 预算输入文本，语义同 [centsToInitialBalanceText]。 */
internal fun centsToBudgetText(cents: Long): String = centsToInitialBalanceText(cents)
