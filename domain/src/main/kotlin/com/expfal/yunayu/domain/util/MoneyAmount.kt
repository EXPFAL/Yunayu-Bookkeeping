package com.expfal.yunayu.domain.util

/**
 * 金额文本 ↔「分」的统一解析。
 *
 * 仅接受 ASCII 数字与至多一个小数点，小数位 ≤2；溢出返回 null。
 * UI 层输入过滤（位数上限）与业务是否允许 0 由调用方决定。
 */
object MoneyAmount {

    /** 小数部分最多位数。 */
    const val MAX_FRACTION_DIGITS = 2

    /**
     * 将金额文本解析为「分」。
     *
     * @param allowZero 为 true 时允许结果为 0（如期初余额）；为 false 时 ≤0 返回 null。
     * @return 空串（[allowZero]=false）、非法文本、溢出或不满足正数约束时返回 null；
     *         [allowZero]=true 且空串时返回 0。
     */
    fun parseToCents(text: String, allowZero: Boolean = false): Long? {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return if (allowZero) 0L else null
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
        return when {
            allowZero && total >= 0L -> total
            !allowZero && total > 0L -> total
            else -> null
        }
    }
}
