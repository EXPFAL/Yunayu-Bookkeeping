package com.expfal.yunayu.ui.util

import java.util.Locale

/**
 * 将「分」格式化为带千分位的元金额字符串，如 123456 → "1,234.56"。
 *
 * 固定两位小数 + 千分位逗号 + [Locale.US]（点号作小数点），供记账金额与预算金额
 * 展示统一复用，避免各处重复格式化逻辑。
 */
fun formatCents(cents: Long): String = String.format(Locale.US, "%,.2f", cents / 100.0)
