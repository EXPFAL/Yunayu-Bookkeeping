package com.expfal.yunayu.domain.report

/**
 * 报告分析引擎接缝。
 *
 * 职责：依据系统指令与结构化统计数据发起推理，返回分析文本。本模块只消费该接口、不关心引擎实现；
 * 当前由 :data 的在线实现 ApiReportAnalyzer 提供，未来切端侧引擎只需替换装配、本模块零改动。
 */
interface ReportAnalyzer {

    /** 引擎是否已就绪可用。 */
    suspend fun isAvailable(): Boolean

    /** 依据系统指令与数据文本推理，返回分析文本；引擎不可用或推理出错时返回 `null`。 */
    suspend fun analyze(systemInstruction: String, dataText: String): String?
}
