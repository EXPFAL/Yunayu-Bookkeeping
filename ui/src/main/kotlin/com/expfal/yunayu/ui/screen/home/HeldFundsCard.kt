package com.expfal.yunayu.ui.screen.home

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.expfal.yunayu.domain.model.AccountBalance
import com.expfal.yunayu.ui.util.formatCents

/**
 * 首页「持有资金」卡片：口径 = 期初余额总和 + 累计净结余（含账户口径）。
 *
 * 正结余以常规色展示并标注「含期初余额」；负结余以 error 色展示并标注「已超支」，
 * 金额符号统一以「-¥」前置表达方向。
 *
 * 当存在具名账户分组时，在总计行上方按账户逐行展示余额（各账户余额 = 期初 + 交易净额 +
 * 转账净额；「未指定账户」无期初与转账，仅交易净额，置末位）；各账户余额之和 + 未指定账户
 * = 总计（恒等式由数据层聚合保证：总资金 = 期初总和 + 累计净结余 = Σ账户余额 + 未指定净额）。
 * 分组为空或仅一条未指定账户行时不渲染分组区，仅显示总计。
 */
@Composable
fun HeldFundsCard(
    heldCents: Long,
    heldByAccount: List<AccountBalance> = emptyList(),
    modifier: Modifier = Modifier,
) {
    val isOverspent = heldCents < 0L
    val amountText = (if (isOverspent) "-¥ " else "¥ ") + formatCents(if (isOverspent) -heldCents else heldCents)
    val subtitle = if (isOverspent) "已超支 · 含期初余额" else "含期初余额 · 净结余"
    val accentColor = if (isOverspent) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    val subColor = if (isOverspent) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
    val showBreakdown = heldByAccount.any { it.accountId != null }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .background(heldFundsCardBrush())
                .padding(24.dp),
        ) {
            Text(
                "持有资金",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (showBreakdown) {
                Spacer(Modifier.height(12.dp))
                AccountBreakdown(heldByAccount)
                Spacer(Modifier.height(12.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(Modifier.height(12.dp))
            }
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

/** 品牌渐变背景：浅色樱粉→奶油两色渐变，深色暗化暖紫三色渐变，随系统深浅色分支。 */
@Composable
private fun heldFundsCardBrush(): Brush {
    val scheme = MaterialTheme.colorScheme
    return if (isSystemInDarkTheme()) {
        Brush.linearGradient(
            colors = listOf(scheme.primaryContainer, scheme.secondaryContainer, scheme.surface),
        )
    } else {
        Brush.linearGradient(
            colors = listOf(scheme.primaryContainer, scheme.surface),
        )
    }
}

/** 按账户逐行展示余额（含期初）：具名账户在前（保持原有顺序），「未指定账户」置末位。 */
@Composable
private fun AccountBreakdown(balances: List<AccountBalance>) {
    val (named, unspecified) = balances.partition { it.accountId != null }
    Column {
        (named + unspecified).forEach { balance ->
            AccountBalanceRow(balance)
        }
    }
}

/** 单个账户净额行：左侧账户名，右侧带符号金额（正「+¥」、负「-¥」）。 */
@Composable
private fun AccountBalanceRow(balance: AccountBalance) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            balance.accountName ?: "未指定",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            formatSignedBalance(balance.balanceCents),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/** 将带符号净额（分）格式化为带方向与货币符号的金额，如 -1234 → "-¥ 12.34"、5678 → "+¥ 56.78"。 */
private fun formatSignedBalance(cents: Long): String {
    val sign = when {
        cents > 0L -> "+¥ "
        cents < 0L -> "-¥ "
        else -> "¥ "
    }
    val magnitude = if (cents < 0L) -cents else cents
    return sign + formatCents(magnitude)
}
