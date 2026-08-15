package com.expfal.yunayu.ui.screen.home

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.expfal.yunayu.domain.model.RecentTransaction
import com.expfal.yunayu.domain.model.TransactionType
import com.expfal.yunayu.ui.util.formatCents
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** 首页「最近记录」卡片：标题 + 最近交易列表，空态仅保留简短占位语；金额与记账口径一致（支出不带负号）。 */
@Composable
fun RecentTransactionsCard(
    uiState: HomeUiState,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth()) {
        Text("最近记录", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        when {
            uiState.loading -> Text(
                "加载中…",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            uiState.recent.isEmpty() -> Text(
                "暂无记录",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            else -> LazyColumn(Modifier.fillMaxWidth()) {
                items(uiState.recent, key = { it.id }) { transaction ->
                    RecentTransactionRow(transaction)
                }
            }
        }
    }
}

/** 单条最近记录行：标签名 + 时间（左），金额（右），收入以「+」前缀与主色区方向。 */
@Composable
private fun RecentTransactionRow(transaction: RecentTransaction) {
    val isIncome = transaction.type == TransactionType.INCOME
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(transaction.tagName ?: "未分类", style = MaterialTheme.typography.bodyMedium)
            Text(
                formatTime(transaction.occurredAt),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = (if (isIncome) "+" else "") + formatCents(transaction.amountCents),
            style = MaterialTheme.typography.bodyMedium,
            color = if (isIncome) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        )
    }
}

/** 毫秒 → 「MM-dd HH:mm」本地时间文本（固定 Locale.US 保证跨设备一致）。 */
private fun formatTime(millis: Long): String =
    Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).format(TIME_FORMAT)

private val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("MM-dd HH:mm", Locale.US)
