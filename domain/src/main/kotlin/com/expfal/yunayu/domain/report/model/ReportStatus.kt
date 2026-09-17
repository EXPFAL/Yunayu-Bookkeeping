package com.expfal.yunayu.domain.report.model

/** 报告生成状态。 */
enum class ReportStatus {
    /** 生成成功（结构化数据完整，分析文本可能为空）。 */
    SUCCESS,

    /** 生成失败（结构化数据仍在，分析文本为空，可手动重试）。 */
    FAILED,

    /**
     * 数据已变更（交易改删等标脏）：结构化数据与旧分析可能仍在，需手动重试重新生成。
     * 与 [FAILED] 区分，避免把「过期」误显示为「生成失败」。
     */
    STALE,
}
