package com.expfal.yunayu.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.expfal.yunayu.domain.report.model.CategoryShare

/**
 * 饼状图 Composable：Canvas drawArc 自绘，不引入第三方图表库。
 *
 * 当总金额 [totalCents] ≤ 0 时不渲染。配色采用固定色板循环，保证视觉稳定性；
 * 图例展示标签名 + 占比百分数。
 *
 * @param shares 分类占比数据列表
 * @param totalCents 总金额（分），用于判断是否渲染
 * @param modifier 修饰符
 */
@Composable
fun PieChart(
    shares: List<CategoryShare>,
    totalCents: Long,
    modifier: Modifier = Modifier,
) {
    if (totalCents <= 0L || shares.isEmpty()) return

    val colors = PIE_COLORS
    Column(modifier = modifier) {
        // 饼图
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp),
        ) {
            val canvasSize = size.minDimension
            val radius = canvasSize / 2f
            val strokeWidth = radius * 0.4f
            val topLeft = Offset(
                (size.width - canvasSize) / 2f,
                (size.height - canvasSize) / 2f,
            )
            val arcSize = Size(canvasSize, canvasSize)

            var startAngle = -90f // 从顶部开始
            shares.forEachIndexed { index, share ->
                val sweepAngle = (share.cents.toFloat() / totalCents) * 360f
                drawArc(
                    color = colors[index % colors.size],
                    startAngle = startAngle,
                    sweepAngle = sweepAngle,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokeWidth),
                )
                startAngle += sweepAngle
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 图例
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            shares.forEachIndexed { index, share ->
                LegendItem(
                    color = colors[index % colors.size],
                    label = share.tagName ?: "未分类",
                    percent = share.percent,
                )
            }
        }
    }
}

/** 图例单行：色块 + 标签名 + 占比百分数。 */
@Composable
private fun LegendItem(color: Color, label: String, percent: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Canvas(modifier = Modifier.size(12.dp)) {
            drawCircle(color = color)
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = "$percent%",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** 固定色板：保证视觉稳定性，循环使用。 */
private val PIE_COLORS = listOf(
    Color(0xFF4CAF50), // 绿色
    Color(0xFF2196F3), // 蓝色
    Color(0xFFFF9800), // 橙色
    Color(0xFFE91E63), // 粉色
    Color(0xFF9C27B0), // 紫色
    Color(0xFF00BCD4), // 青色
    Color(0xFFFF5722), // 深橙
    Color(0xFF795548), // 棕色
)
