package com.expfal.yunayu.domain.model

/** 时间窗内单个分类（或未分类）的支出聚合，金额一律以「分」为单位。 */
data class CategoryExpense(
    val tagName: String?,
    val cents: Long,
)
