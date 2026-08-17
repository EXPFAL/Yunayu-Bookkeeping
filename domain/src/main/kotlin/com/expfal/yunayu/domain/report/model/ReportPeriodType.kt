package com.expfal.yunayu.domain.report.model

/** 报告周期类型。 */
enum class ReportPeriodType {
    /** 周度报告（本周一 00:00 至当前或整周）。 */
    WEEKLY,

    /** 月度报告。 */
    MONTHLY,

    /** 年度报告。 */
    ANNUAL,
}
