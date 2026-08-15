package com.expfal.yunayu.domain.report.model

/** 报告生成状态。 */
enum class ReportStatus {
    /** 生成成功（结构化数据完整，分析文本可能为空）。 */
    SUCCESS,

    /** 生成失败（结构化数据仍在，分析文本为空，可手动重试）。 */
    FAILED,
}
