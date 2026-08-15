package com.expfal.yunayu.domain.report.model

/**
 * 分类支出占比行：标签名（未分类为 `null`）、金额（分）与占比（百分比整数 0-100）。
 */
data class CategoryShare(
    val tagName: String?,
    val cents: Long,
    val percent: Int,
)
