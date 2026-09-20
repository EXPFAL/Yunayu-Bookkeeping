package com.expfal.yunayu.domain.model

/**
 * 某一支出分类下抽到的代表备注（供报告 AI 理解消费场景）。
 *
 * [tagId]/[tagName] 均为 `null` 表示「未分类」；[notes] 已按金额降序截断。
 */
data class CategoryNoteSample(
    val tagId: Long?,
    val tagName: String?,
    val notes: List<String>,
)
