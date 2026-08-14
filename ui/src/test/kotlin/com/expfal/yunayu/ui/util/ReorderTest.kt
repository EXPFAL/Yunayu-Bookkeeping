package com.expfal.yunayu.ui.util

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** [moveItem] 与 [reorderTargetIndex] 纯函数的 JVM 单元测试。 */
class ReorderTest {

    @Test
    fun `moveItem moves element forward`() {
        assertEquals(listOf(2, 3, 1), moveItem(listOf(1, 2, 3), 0, 2))
        assertEquals(listOf(1, 3, 2), moveItem(listOf(1, 2, 3), 1, 2))
    }

    @Test
    fun `moveItem moves element backward`() {
        assertEquals(listOf(3, 1, 2), moveItem(listOf(1, 2, 3), 2, 0))
        assertEquals(listOf(2, 1, 3), moveItem(listOf(1, 2, 3), 1, 0))
    }

    @Test
    fun `moveItem clamps bounds and handles no-op`() {
        assertEquals(listOf(2, 3, 1), moveItem(listOf(1, 2, 3), 0, 99))
        assertEquals(listOf(3, 1, 2), moveItem(listOf(1, 2, 3), 2, -5))
        assertEquals(listOf(1, 2, 3), moveItem(listOf(1, 2, 3), 1, 1))
        assertEquals(emptyList<Int>(), moveItem(emptyList<Int>(), 0, 0))
        assertEquals(listOf(1), moveItem(listOf(1), 0, 0))
    }

    @Test
    fun `reorderTargetIndex converts offset to row delta`() {
        assertEquals(0, reorderTargetIndex(5, 100, 0f))
        assertEquals(0, reorderTargetIndex(5, 100, 49f))
        assertEquals(1, reorderTargetIndex(5, 100, 50f))
        assertEquals(2, reorderTargetIndex(5, 100, 150f))
        assertEquals(-1, reorderTargetIndex(5, 100, -60f))
    }

    @Test
    fun `reorderTargetIndex clamps to bounds and guards degenerate input`() {
        assertEquals(4, reorderTargetIndex(5, 100, 9999f))
        assertEquals(-4, reorderTargetIndex(5, 100, -9999f))
        assertEquals(0, reorderTargetIndex(1, 100, 500f))
        assertEquals(0, reorderTargetIndex(5, 0, 500f))
    }
}
