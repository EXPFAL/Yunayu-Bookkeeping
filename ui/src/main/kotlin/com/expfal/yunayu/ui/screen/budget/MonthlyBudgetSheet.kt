package com.expfal.yunayu.ui.screen.budget

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.expfal.yunayu.ui.util.filterBudgetInput
import com.expfal.yunayu.ui.util.parseBudgetToCents
import com.expfal.yunayu.ui.util.vibrateSuccess
import java.util.Locale

/**
 * 月度预算设置底部弹层：仅金额输入（小数过滤）+ 保存。
 *
 * 编辑模式从 [MonthlyBudgetViewModel.uiState] 的当前额度预填；保存成功经
 * [MonthlyBudgetEvent.Saved] 触发震动并关闭，失败经 [MonthlyBudgetEvent.SaveFailed] 显示
 * 温和错误文案「刚才没保存上，再试一次」。saving 期间禁止关闭与重复提交。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MonthlyBudgetSheet(
    viewModel: MonthlyBudgetViewModel,
    onDismissRequest: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var budgetText by remember { mutableStateOf(centsToBudgetText(uiState.budgetCents)) }
    var saveFailed by remember { mutableStateOf(false) }
    val context = LocalContext.current

    LaunchedEffect(viewModel, context) {
        viewModel.events.collect { event ->
            when (event) {
                MonthlyBudgetEvent.Saved -> {
                    context.vibrateSuccess()
                    onDismissRequest()
                }
                MonthlyBudgetEvent.SaveFailed -> saveFailed = true
            }
        }
    }

    val budgetCents = parseBudgetToCents(budgetText)

    ModalBottomSheet(onDismissRequest = { if (!uiState.saving) onDismissRequest() }) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 28.dp),
        ) {
            // 主题 typography 已覆写 titleLarge 为加粗 + tnum（对齐金额展示），
            // 标题为非金额文本，局部回退字重 Normal 保持字号层级不变。
            Text(
                "设置每月预算",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Normal),
            )
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = budgetText,
                onValueChange = { budgetText = filterBudgetInput(it) },
                label = { Text("每月预算（元）") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )
            if (saveFailed) {
                Spacer(Modifier.height(12.dp))
                Text(
                    "刚才没保存上，再试一次",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Spacer(Modifier.height(20.dp))
            Button(
                onClick = {
                    val cents = budgetCents
                    if (cents != null) {
                        saveFailed = false
                        viewModel.saveMonthlyBudget(cents)
                    }
                },
                enabled = !uiState.saving && budgetCents != null,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
            ) {
                Text(if (uiState.saving) "保存中…" else "保存")
            }
        }
    }
}

/** 分 → 预算输入文本：非正数返回空串；整数元省略小数，否则保留两位。 */
private fun centsToBudgetText(cents: Long): String =
    if (cents <= 0L) {
        ""
    } else if (cents % 100L == 0L) {
        (cents / 100L).toString()
    } else {
        String.format(Locale.US, "%.2f", cents / 100.0)
    }
