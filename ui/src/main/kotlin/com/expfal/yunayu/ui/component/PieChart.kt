package com.expfal.yunayu.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.expfal.yunayu.domain.report.model.CategoryShare
import com.expfal.yunayu.ui.util.formatCents

/**
 * 可交互环形图：Canvas 自绘，点扇区选中，点内孔取消选中；圆心展示当前分类金额与占比。
 *
 * 图例由报告页的分类列表承担，这里只画环。配色按 index 循环，不引入第三方图表库。
 */
@Composable
fun PieChart(
    shares: List<CategoryShare>,
    totalCents: Long,
    modifier: Modifier = Modifier,
    selectedIndex: Int? = null,
    onShareSelected: (Int) -> Unit = {},
    onSelectionCleared: () -> Unit = {},
) {
    if (totalCents <= 0L || shares.isEmpty()) return

    val colors = PIE_COLORS
    val selected = selectedIndex?.takeIf { it in shares.indices }
    val centerShare = selected?.let { shares[it] }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(200.dp),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .pointerInput(shares, totalCents, selected) {
                    detectTapGestures { offset ->
                        val layout = donutLayout(minOf(size.width, size.height).toFloat())
                        val stroke = if (selected != null) {
                            layout.selectedStrokeWidth
                        } else {
                            layout.strokeWidth
                        }
                        val pathRadius = layout.arcDiameter / 2f
                        val center = Offset(size.width / 2f, size.height / 2f)
                        val dx = offset.x - center.x
                        val dy = offset.y - center.y
                        val innerRadius = pathRadius - stroke / 2f
                        if (isInsideDonutHole(dx, dy, innerRadius)) {
                            onSelectionCleared()
                            return@detectTapGestures
                        }
                        val visualOuter = pathRadius + stroke / 2f
                        if (!isOnDonutRing(dx, dy, visualOuter, stroke)) return@detectTapGestures
                        val deg = touchToPieDegreesFromTop(dx, dy)
                        val index = hitTestPieShareIndex(
                            degreesFromTop = deg,
                            centsList = shares.map { it.cents },
                            totalCents = totalCents,
                        ) ?: return@detectTapGestures
                        onShareSelected(index)
                    }
                },
        ) {
            val layout = donutLayout(size.minDimension)
            val diameter = layout.arcDiameter
            val topLeft = Offset(
                (size.width - diameter) / 2f,
                (size.height - diameter) / 2f,
            )
            val arcSize = Size(diameter, diameter)

            var startAngle = -90f
            shares.forEachIndexed { index, share ->
                val sweepAngle = (share.cents.toFloat() / totalCents) * 360f
                val drawStroke = if (index == selected) layout.selectedStrokeWidth else layout.strokeWidth
                drawArc(
                    color = colors[index % colors.size],
                    startAngle = startAngle,
                    sweepAngle = sweepAngle,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = drawStroke),
                )
                startAngle += sweepAngle
            }
        }

        if (centerShare != null) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onSelectionCleared,
                ),
            ) {
                Text(
                    text = centerShare.tagName ?: "未分类",
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.width(96.dp),
                )
                Text(
                    text = formatCents(centerShare.cents),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = "${centerShare.percent}%",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** 固定色板：保证视觉稳定性，循环使用。对外暴露供分类列表色点对齐。 */
val PIE_COLORS: List<Color> = listOf(
    Color(0xFF4CAF50),
    Color(0xFF2196F3),
    Color(0xFFFF9800),
    Color(0xFFE91E63),
    Color(0xFF9C27B0),
    Color(0xFF00BCD4),
    Color(0xFFFF5722),
    Color(0xFF795548),
)
