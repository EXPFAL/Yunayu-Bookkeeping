package com.expfal.yunayu.ui.util

import kotlin.math.roundToInt

/**
 * 将累计纵向拖拽偏移换算为目标索引偏移量（相对拖拽起点应移动的行数）。
 *
 * 以 [itemHeight]（像素）为单行高度，[dragOffsetY]（像素，正=向下拖）除以行高后四舍五入取整；
 * 结果夹取到 `[-(itemCount - 1), itemCount - 1]`，保证叠加到起点索引后仍落在
 * `[0, itemCount - 1]` 内。行数不足两个或行高非法时一律返回 0（无需移动）。
 */
fun reorderTargetIndex(itemCount: Int, itemHeight: Int, dragOffsetY: Float): Int {
    if (itemCount <= 1 || itemHeight <= 0) return 0
    val delta = (dragOffsetY / itemHeight).roundToInt()
    return delta.coerceIn(-(itemCount - 1), itemCount - 1)
}

/**
 * 将 [list] 中 `from` 索引处的元素移动到 `to` 索引处，其余元素保持相对顺序不变。
 *
 * 纯函数：不改动入参，返回新列表。[from] / [to] 越界时分别夹取到 `[0, size-1]`；
 * 夹取后若 `from == to`，或列表元素不足两个，则返回原列表引用（无移动发生）。
 */
fun <T> moveItem(list: List<T>, from: Int, to: Int): List<T> {
    if (list.size < 2) return list
    val safeFrom = from.coerceIn(0, list.lastIndex)
    val safeTo = to.coerceIn(0, list.lastIndex)
    if (safeFrom == safeTo) return list
    val result = list.toMutableList()
    val item = result.removeAt(safeFrom)
    result.add(safeTo, item)
    return result
}
