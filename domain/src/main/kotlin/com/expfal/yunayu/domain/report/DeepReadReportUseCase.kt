package com.expfal.yunayu.domain.report

import com.expfal.yunayu.domain.nl.NLTransactionParser
import com.expfal.yunayu.domain.report.model.LocalInsightKind
import com.expfal.yunayu.domain.report.model.Report
import com.expfal.yunayu.domain.report.model.ReportSeriesSnapshot
import com.expfal.yunayu.domain.repository.ReportRepository

/**
 * 按需 AI 深读：把本地已算事实写成复盘段落，写入 [Report.analysisText]。
 * 失败返回 null，不改变报告主体状态。
 */
class DeepReadReportUseCase(
    private val nlParser: NLTransactionParser,
    private val reportRepository: ReportRepository,
) {

    suspend operator fun invoke(
        report: Report,
        series: ReportSeriesSnapshot?,
    ): String? {
        if (!nlParser.isAvailable()) return null
        val story = report.localInsights
            .firstOrNull { it.kind == LocalInsightKind.STORY }
            ?.detail
            .orEmpty()
        val advice = report.localInsights
            .filter { it.kind == LocalInsightKind.ADVICE }
            .joinToString("；") { it.title }
        val topCats = report.topCategories.take(5).joinToString("，") { share ->
            val name = share.tagName ?: "未分类"
            "$name ${share.percent}%"
        }
        val mom = series?.mom
        val facts = buildString {
            appendLine("周期=${report.periodType.name} 期键=${report.periodKey}")
            appendLine("收入分=${report.incomeCents} 支出分=${report.expenseCents}")
            appendLine("上期收入分=${report.prevIncomeCents} 上期支出分=${report.prevExpenseCents}")
            if (mom != null) {
                appendLine(
                    "环比支出分差=${mom.expenseDeltaCents} 环比净结余分差=${mom.netDeltaCents}",
                )
            }
            appendLine("Top分类=$topCats")
            if (story.isNotBlank()) appendLine("本地故事=$story")
            if (advice.isNotBlank()) appendLine("本地建议=$advice")
        }.take(1_200)

        val text = nlParser.generate(SYSTEM, facts)?.trim().orEmpty()
        if (text.isBlank()) return null

        val clipped = text.take(MAX_CHARS)
        val existing = reportRepository.getByKey(report.periodType, report.periodKey) ?: report
        reportRepository.upsert(existing.copy(analysisText = clipped))
        return clipped
    }

    private companion object {
        const val MAX_CHARS = 800
        const val SYSTEM =
            "你是个人账本复盘助手。仅依据用户给出的结构化事实，用简体中文写一段连贯复盘" +
                "（含 1～2 条扩展建议），不超过 400 字。不要编造未给出的数字，不要输出标题或列表符号堆砌。"
    }
}
