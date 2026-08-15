package com.expfal.yunayu.ui.util

import com.expfal.yunayu.domain.model.TransactionType
import java.util.Locale

/**
 * 将「分」格式化为带千分位的元金额字符串，如 123456 → "1,234.56"。
 *
 * 固定两位小数 + 千分位逗号 + [Locale.US]（点号作小数点），供记账金额与预算金额
 * 展示统一复用，避免各处重复格式化逻辑。
 */
fun formatCents(cents: Long): String = String.format(Locale.US, "%,.2f", cents / 100.0)

/**
 * 将「分」格式化为带方向的元金额字符串：收入返回「+格式值」、支出返回「-格式值」
 * （ASCII 符号，与持有资金卡既有 ASCII `-` 口径统一）。
 *
 * 仅展示层符号，不改变存储口径（`amountCents` 恒为正数）。
 */
fun formatSignedCents(cents: Long, type: TransactionType): String =
    when (type) {
        TransactionType.INCOME -> "+" + formatCents(cents)
        TransactionType.EXPENSE -> "-" + formatCents(cents)
    }
