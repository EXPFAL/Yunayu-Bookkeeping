package com.expfal.yunayu.ui.util

import com.expfal.yunayu.domain.model.Tag

/**
 * 计算标签在建议分类 chip 上的展示名（PRD P0-3 子标签兼容）。
 *
 * 根标签（`parentId == null`）或父名映射缺失时直接返回 [Tag.name]；子标签且父名可解析时
 * 返回「父·子」两级名称，避免不同父级下的同名子标签在快捷记账里无法区分。
 */
fun tagDisplayName(tag: Tag, rootNameById: Map<Long, String>): String {
    val parentId = tag.parentId ?: return tag.name
    val parentName = rootNameById[parentId] ?: return tag.name
    return "$parentName·${tag.name}"
}
