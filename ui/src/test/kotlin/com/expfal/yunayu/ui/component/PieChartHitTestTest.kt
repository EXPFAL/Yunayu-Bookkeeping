package com.expfal.yunayu.ui.component

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** [hitTestPieShareIndex] / [touchToPieDegreesFromTop] 纯函数单测。 */
class PieChartHitTestTest {

    @Test
    fun `hitTest maps top of first equal slice to index 0`() {
        val cents = listOf(50L, 50L)
        assertEquals(0, hitTestPieShareIndex(0f, cents, 100L))
        assertEquals(0, hitTestPieShareIndex(90f, cents, 100L))
        assertEquals(1, hitTestPieShareIndex(180f, cents, 100L))
        assertEquals(1, hitTestPieShareIndex(270f, cents, 100L))
    }

    @Test
    fun `hitTest returns null when total is zero`() {
        assertNull(hitTestPieShareIndex(10f, listOf(1L), 0L))
        assertNull(hitTestPieShareIndex(10f, emptyList(), 100L))
    }

    @Test
    fun `touchToPieDegreesFromTop maps up as zero`() {
        // 向上：dx=0, dy=-1 → 顶部
        assertEquals(0f, touchToPieDegreesFromTop(0f, -1f), 0.01f)
        // 向右：dx=1, dy=0 → 90°
        assertEquals(90f, touchToPieDegreesFromTop(1f, 0f), 0.01f)
    }

    @Test
    fun `isOnDonutRing accepts mid-stroke distance`() {
        val outer = 100f
        val stroke = 40f
        assertTrue(isOnDonutRing(0f, -(outer - stroke / 2f), outer, stroke))
    }

    @Test
    fun `selected stroke stays inside the canvas`() {
        val canvas = 200f
        val layout = donutLayout(canvas)
        val visualOuter = layout.arcDiameter / 2f + layout.selectedStrokeWidth / 2f
        assertTrue(visualOuter <= canvas / 2f)
    }

    @Test
    fun `center tap is inside the hole and not on the ring`() {
        val layout = donutLayout(200f)
        val pathRadius = layout.arcDiameter / 2f
        val inner = pathRadius - layout.strokeWidth / 2f
        assertTrue(isInsideDonutHole(0f, 0f, inner))
        assertFalse(isOnDonutRing(0f, 0f, pathRadius + layout.strokeWidth / 2f, layout.strokeWidth))
    }
}
