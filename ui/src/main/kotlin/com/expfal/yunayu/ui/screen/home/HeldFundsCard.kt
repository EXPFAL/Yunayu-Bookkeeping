package com.expfal.yunayu.ui.screen.home

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.expfal.yunayu.ui.util.formatCents

/**
 * 首页「持有资金」卡片：口径 = 累计收入 − 累计支出（全历史净结余）。
 *
 * 正结余以常规色展示并标注「自记账日起净结余」；负结余以 error 色展示并标注「已超支」，
 * 金额符号统一以「-¥」前置表达方向。
 */
@Composable
fun HeldFundsCard(
    heldCents: Long,
    modifier: Modifier = Modifier,
) {
    val isOverspent = heldCents < 0L
    val amountText = (if (isOverspent) "-¥ " else "¥ ") + formatCents(if (isOverspent) -heldCents else heldCents)
    val subtitle = if (isOverspent) "已超支 · 自记账日起净结余" else "自记账日起净结余"
    val accentColor = if (isOverspent) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    val subColor = if (isOverspent) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant

    Card(modifier.fillMaxWidth()) {
        Column(Modifier.padding(24.dp)) {
            Text(
                "持有资金",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                amountText,
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = accentColor,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = subColor,
            )
        }
    }
}
