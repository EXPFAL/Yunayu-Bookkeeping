package com.expfal.yunayu.ui.component

import kotlin.math.atan2
import kotlin.math.sqrt

/**
 * 将触点相对圆心的坐标转为「自顶部顺时针」的角度 [0, 360)。
 *
 * Canvas `drawArc` 以 -90° 为顶部起点、顺时针扫角，与本函数口径一致。
 */
fun touchToPieDegreesFromTop(dx: Float, dy: Float): Float {
    val degFromRight = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
    return (degFromRight + 90f + 360f) % 360f
}

/**
 * 按各扇区金额占比，将 [degreesFromTop] 映射到 [centsList] 下标；总金额 ≤0 或列表空返回 null。
 *
 * 过小扇区仍可命中（按真实扫角）；无最小角放大，图例点击作为兜底。
 */
fun hitTestPieShareIndex(
    degreesFromTop: Float,
    centsList: List<Long>,
    totalCents: Long,
): Int? {
    if (totalCents <= 0L || centsList.isEmpty()) return null
    val angle = ((degreesFromTop % 360f) + 360f) % 360f
    var start = 0f
    centsList.forEachIndexed { index, cents ->
        if (cents <= 0L) return@forEachIndexed
        val sweep = (cents.toFloat() / totalCents.toFloat()) * 360f
        val end = start + sweep
        if (angle >= start && angle < end) return index
        // 浮点闭合：最后一扇区吃掉 360 边界
        if (index == centsList.lastIndex && angle >= start && angle <= 360f) return index
        start = end
    }
    return null
}

/**
 * 环形图在边长为 [canvasMin] 的正方形里的尺寸。
 * 选中描边加粗后，外缘仍停在画布内侧。
 */
data class DonutLayout(
    val arcDiameter: Float,
    val strokeWidth: Float,
    val selectedStrokeWidth: Float,
)

fun donutLayout(canvasMin: Float): DonutLayout {
    val selectedStroke = canvasMin * 0.18f
    val stroke = selectedStroke / SELECTED_STROKE_SCALE
    val diameter = (canvasMin - selectedStroke - 2f).coerceAtLeast(0f)
    return DonutLayout(diameter, stroke, selectedStroke)
}

/** 触点是否落在环形内孔（用于点中心取消选中）。 */
fun isInsideDonutHole(dx: Float, dy: Float, innerRadius: Float): Boolean {
    if (innerRadius <= 0f) return false
    return sqrt(dx * dx + dy * dy) < innerRadius
}

/** 触点是否落在环形（甜甜圈）描线带内。 */
fun isOnDonutRing(
    dx: Float,
    dy: Float,
    outerRadius: Float,
    strokeWidth: Float,
): Boolean {
    val dist = sqrt(dx * dx + dy * dy)
    val half = strokeWidth / 2f
    val mid = outerRadius - half
    return dist in (mid - half)..(mid + half)
}

private const val SELECTED_STROKE_SCALE = 1.15f
