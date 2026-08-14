package com.expfal.yunayu.ui.screen.budget

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.expfal.yunayu.domain.model.BudgetPhase
import com.expfal.yunayu.domain.model.BudgetSnapshot
import com.expfal.yunayu.domain.model.Semester
import com.expfal.yunayu.ui.util.formatCents

/** 首页预算看板卡片：有学期时展示周额度与进度，无学期时引导设置，加载中显示占位。 */
@Composable
fun BudgetCard(
    loading: Boolean,
    semester: Semester?,
    snapshot: BudgetSnapshot?,
    onEdit: () -> Unit,
    onSetup: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when {
        loading -> LoadingCard(modifier)
        semester == null || snapshot == null -> EmptyCard(onSetup, modifier)
        else -> ActiveCard(semester, snapshot, onEdit, modifier)
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
            Text("设置学期预算，看清每周能花多少", style = MaterialTheme.typography.titleMedium)
            TextButton(onClick = onSetup) { Text("去设置") }
        }
    }
}

@Composable
private fun ActiveCard(
    semester: Semester,
    snapshot: BudgetSnapshot,
    onEdit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val ratio = if (snapshot.totalBudgetCents > 0L) {
        snapshot.spentCents.toFloat() / snapshot.totalBudgetCents
    } else {
        0f
    }
    val percent = if (snapshot.totalBudgetCents > 0L) {
        (snapshot.spentCents * 100L / snapshot.totalBudgetCents).toInt()
    } else {
        0
    }
    val progressColor = when {
        ratio >= 1f -> MaterialTheme.colorScheme.error
        ratio >= 0.8f -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.primary
    }
    Card(onClick = onEdit, modifier.fillMaxWidth()) {
        Column(Modifier.padding(24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    semester.name,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                PhasePill(snapshot.phase)
            }
            Spacer(Modifier.height(12.dp))
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
                "剩余 ${snapshot.remainingDays} 天 · 已用 $percent%",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "学期预算还剩 ¥${formatCents(snapshot.remainingCents)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PhasePill(phase: BudgetPhase) {
    val label = when (phase) {
        BudgetPhase.NORMAL -> "常规"
        BudgetPhase.EXAM_WEEK -> "考试周"
        BudgetPhase.VACATION -> "假期"
    }
    Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.secondaryContainer) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}
