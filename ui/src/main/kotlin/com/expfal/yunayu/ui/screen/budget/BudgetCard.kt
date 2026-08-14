package com.expfal.yunayu.ui.screen.budget

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.expfal.yunayu.domain.model.MonthlyBudgetSnapshot
import com.expfal.yunayu.ui.util.formatCents

/**
 * 首页月度预算看板卡片：有预算时展示周额度与进度，未设置时引导设置，加载中显示占位。
 *
 * 三态：loading 占位；budgetCents == 0 引导态；否则激活态（整卡可点进入编辑）。
 */
@Composable
fun BudgetCard(
    loading: Boolean,
    budgetCents: Long,
    snapshot: MonthlyBudgetSnapshot?,
    onEdit: () -> Unit,
    onSetup: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val activeSnapshot = snapshot
    when {
        loading -> LoadingCard(modifier)
        budgetCents == 0L -> EmptyCard(onSetup, modifier)
        activeSnapshot != null -> ActiveCard(activeSnapshot, onEdit, modifier)
        else -> LoadingCard(modifier)
    }
}

@Composable
private fun LoadingCard(modifier: Modifier = Modifier) {
    Card(modifier.fillMaxWidth()) {
        Text(
            "加载中…",
            Modifier.padding(24.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun EmptyCard(onSetup: () -> Unit, modifier: Modifier = Modifier) {
    Card(modifier.fillMaxWidth()) {
        Column(Modifier.padding(24.dp)) {
            Text("设置每月预算，看清每周能花多少", style = MaterialTheme.typography.titleMedium)
            TextButton(onClick = onSetup) { Text("去设置") }
        }
    }
}

@Composable
private fun ActiveCard(
    snapshot: MonthlyBudgetSnapshot,
    onEdit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val budget = snapshot.monthlyBudgetCents
    val ratio = if (budget > 0L) snapshot.spentCents.toFloat() / budget else 0f
    val progressColor = when {
        ratio >= 1f -> MaterialTheme.colorScheme.error
        ratio >= 0.8f -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.primary
    }
    Card(onClick = onEdit, modifier.fillMaxWidth()) {
        Column(Modifier.padding(24.dp)) {
            Text(
                "本周还可花",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "¥ " + formatCents(snapshot.weeklyQuotaCents),
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { ratio.coerceIn(0f, 1f) },
                color = progressColor,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "本月已花 ¥${formatCents(snapshot.spentCents)} · 剩余 ${snapshot.remainingDays} 天",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (ratio >= 1f) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "这个月的预算用完了，先记着，回头看看哪里能省",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}
