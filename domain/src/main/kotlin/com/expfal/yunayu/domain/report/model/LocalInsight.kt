package com.expfal.yunayu.domain.report.model

/** 本地规则洞察类型。 */
enum class LocalInsightKind {
    /** 历史报告可能仍存环比句；新生成不再写入。 */
    TREND,

    /** 分类结构。 */
    STRUCTURE,

    /** 预算进度。 */
    BUDGET,

    /** 净结余 / 空窗等汇总。 */
    SUMMARY,

    /** 未分类等数据质量。 */
    DATA_QUALITY,

    /** 异常消费（B 期规则）。 */
    ANOMALY,
}

/**
 * 一条本地规则洞察（无网络即可生成）。
 *
 * @param kind 类别，供 UI 分组或图标
 * @param title 短标题（列表摘要可取首条）
 * @param detail 说明正文
 */
data class LocalInsight(
    val kind: LocalInsightKind,
    val title: String,
    val detail: String,
)
