package com.expfal.yunayu.domain.model

/** 时间窗内的收支汇总，金额一律以「分」为单位。 */
data class WindowTotals(
    val incomeCents: Long,
    val expenseCents: Long,
)
