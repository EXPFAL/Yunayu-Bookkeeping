package com.expfal.yunayu.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
 * 可交互环形图：Canvas 自绘，点扇区/图例选中；圆心展示当前分类金额与占比。
 *
 * 配色采用固定色板按 index 循环；不引入第三方图表库。
 */
@Composable
fun PieChart(
    shares: List<CategoryShare>,
    totalCents: Long,
    modifier: Modifier = Modifier,
    selectedIndex: Int? = null,
    onShareSelected: (Int) -> Unit = {},
) {
    if (totalCents <= 0L || shares.isEmpty()) return

    val colors = PIE_COLORS
    val selected = selectedIndex?.takeIf { it in shares.indices }
    val centerShare = selected?.let { shares[it] }

    Column(modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .pointerInput(shares, totalCents) {
                        detectTapGestures { offset ->
                            val canvasSize = minOf(size.width, size.height).toFloat()
                            val radius = canvasSize / 2f
                            val strokeWidth = radius * 0.4f
                            val center = Offset(size.width / 2f, size.height / 2f)
                            val dx = offset.x - center.x
                            val dy = offset.y - center.y
                            if (!isOnDonutRing(dx, dy, radius, strokeWidth)) return@detectTapGestures
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
                val canvasSize = size.minDimension
                val radius = canvasSize / 2f
                val strokeWidth = radius * 0.4f
                val topLeft = Offset(
                    (size.width - canvasSize) / 2f,
                    (size.height - canvasSize) / 2f,
                )
                val arcSize = Size(canvasSize, canvasSize)

                var startAngle = -90f
                shares.forEachIndexed { index, share ->
                    val sweepAngle = (share.cents.toFloat() / totalCents) * 360f
                    val isSelected = index == selected
                    val drawStroke = if (isSelected) strokeWidth * 1.15f else strokeWidth
                    val inset = if (isSelected) 0f else strokeWidth * 0.05f
                    val selectedTopLeft = Offset(topLeft.x - inset, topLeft.y - inset)
                    val selectedSize = Size(arcSize.width + inset * 2, arcSize.height + inset * 2)
                    drawArc(
                        color = colors[index % colors.size],
                        startAngle = startAngle,
                        sweepAngle = sweepAngle,
                        useCenter = false,
                        topLeft = if (isSelected) selectedTopLeft else topLeft,
                        size = if (isSelected) selectedSize else arcSize,
                        style = Stroke(width = drawStroke),
                    )
                    startAngle += sweepAngle
                }
            }

            if (centerShare != null) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
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

        Spacer(modifier = Modifier.height(16.dp))

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            shares.forEachIndexed { index, share ->
                LegendItem(
                    color = colors[index % colors.size],
                    label = share.tagName ?: "未分类",
                    percent = share.percent,
                    selected = index == selected,
                    onClick = { onShareSelected(index) },
                )
            }
        }
    }
}

/** 图例单行：色块 + 标签名 + 占比；可点选。 */
@Composable
private fun LegendItem(
    color: Color,
    label: String,
    percent: Int,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .then(
                if (selected) {
                    Modifier
                        .background(
                            MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f),
                            RoundedCornerShape(8.dp),
                        )
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                } else {
                    Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                },
            ),
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
