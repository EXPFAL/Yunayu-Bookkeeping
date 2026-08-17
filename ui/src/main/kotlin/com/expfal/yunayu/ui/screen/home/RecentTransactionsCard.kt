package com.expfal.yunayu.ui.screen.home

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.expfal.yunayu.ui.component.TransactionRow

/** 首页「最近记录」卡片：标题 + 最近交易列表，空态仅保留简短占位语；金额收入「+金额」主色 / 支出「-金额」常规色。 */
@Composable
fun RecentTransactionsCard(
    uiState: HomeUiState,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
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
            else -> LazyColumn(state = listState, modifier = Modifier.fillMaxWidth()) {
                items(uiState.recent, key = { it.id }) { transaction ->
                    TransactionRow(transaction)
                }
            }
        }
    }
}
