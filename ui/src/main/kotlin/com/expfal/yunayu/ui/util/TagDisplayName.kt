package com.expfal.yunayu.ui.util

import com.expfal.yunayu.domain.model.Tag
import com.expfal.yunayu.domain.util.TagTreeLoader

/**
 * 计算标签在建议分类 chip 上的展示名（PRD P0-3 子标签兼容）。
 *
 * 委托 [TagTreeLoader.displayName]：根标签或父名映射缺失时直接返回 [Tag.name]；
 * 子标签且父名可解析时返回「父·子」两级名称。
 */
fun tagDisplayName(tag: Tag, rootNameById: Map<Long, String>): String =
    TagTreeLoader.displayName(tag, rootNameById)
