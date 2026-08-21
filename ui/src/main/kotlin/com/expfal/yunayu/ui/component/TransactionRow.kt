package com.expfal.yunayu.ui.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.expfal.yunayu.domain.model.RecentTransaction
import com.expfal.yunayu.domain.model.TransactionType
import com.expfal.yunayu.ui.util.formatSignedCents
import com.expfal.yunayu.ui.util.formatTime

/**
 * 公共交易行组件：左列标签名 + 时间·账户 + 备注，右列方向化金额。
 *
 * 金额口径：收入「+金额」以 [MaterialTheme.colorScheme.primary] 展示，支出「-金额」以常规色
 * 展示；账户紧随时间之后（未指定账户显示「未指定」）；备注仅在非空且非空白时渲染
 * （最多两行、超出省略）。[trailing] 非空时在金额之后渲染，供删除按钮等插槽扩展使用。
 */
@Composable
fun TransactionRow(
    transaction: RecentTransaction,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
) {
    val isIncome = transaction.type == TransactionType.INCOME
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.fillMaxWidth().padding(vertical = 8.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(transaction.tagName ?: "未分类", style = MaterialTheme.typography.bodyMedium)
            Text(
                "${formatTime(transaction.occurredAt)} · ${transaction.accountName ?: "未指定"}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val note = transaction.note
            if (!note.isNullOrBlank()) {
                Text(
                    note,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Text(
            text = formatSignedCents(transaction.amountCents, transaction.type),
            style = MaterialTheme.typography.bodyMedium,
            color = if (isIncome) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        )
        trailing?.invoke()
    }
}
