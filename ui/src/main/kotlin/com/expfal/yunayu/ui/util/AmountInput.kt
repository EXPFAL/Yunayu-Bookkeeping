package com.expfal.yunayu.ui.util

import com.expfal.yunayu.domain.util.MoneyAmount
import java.util.Locale

/** 记账金额整数部分最大位数（QuickAdd / 编辑交易）。 */
const val MAX_AMOUNT_INTEGER_DIGITS = 7

/**
 * 将金额文本解析为「分」（正数）；委托 [MoneyAmount]，供 VM / Compose 共用。
 */
fun parseAmountToCents(text: String): Long? = MoneyAmount.parseToCents(text, allowZero = false)

/** 将「分」转回可编辑金额文本：整数不带小数、十分位省略尾零。 */
fun centsToAmountText(cents: Long): String {
    val yuan = cents / 100
    val fraction = cents % 100
    return when {
        fraction == 0L -> yuan.toString()
        fraction % 10 == 0L -> "$yuan.${fraction / 10}"
        else -> String.format(Locale.US, "%d.%02d", yuan, fraction)
    }
}

/** 追加一位数字或小数点；超出位数上限或重复小数点时忽略。 */
fun appendAmountDigit(current: String, digit: Char): String {
    if (digit == '.') {
        if (current.contains('.')) return current
        return if (current.isEmpty()) "0." else current + "."
    }
    if (current.contains('.')) {
        val fraction = current.substringAfter('.')
        if (fraction.length >= MoneyAmount.MAX_FRACTION_DIGITS) return current
        return current + digit
    }
    if (current == "0") return digit.toString()
    if (current.length >= MAX_AMOUNT_INTEGER_DIGITS) return current
    return current + digit
}
