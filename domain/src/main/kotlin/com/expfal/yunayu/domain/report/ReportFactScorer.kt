package com.expfal.yunayu.domain.report

import com.expfal.yunayu.domain.model.WindowTotals
import com.expfal.yunayu.domain.report.model.CategoryShare
import com.expfal.yunayu.domain.report.model.LocalInsight
import com.expfal.yunayu.domain.report.model.LocalInsightKind
import com.expfal.yunayu.domain.report.model.MomComparison
import com.expfal.yunayu.domain.report.model.PeriodTotalsPoint
import com.expfal.yunayu.domain.report.model.ReportSeriesSnapshot
import java.util.Locale
import kotlin.math.abs

/** 一条带分数的候选事实句。 */
data class ScoredFact(
    val score: Double,
    val sentence: String,
    val kind: LocalInsightKind,
)

/**
 * 本地事实显著性评分（纯函数）。
 *
 * v1：固定类型权重 × 幅度归一化 × 极值新鲜度（1.0 / 1.15）。
 */
object ReportFactScorer {

    private const val WEIGHT_MOM = 1.2
    private const val WEIGHT_STRUCTURE = 1.0
    private const val WEIGHT_BUDGET = 1.1
    private const val WEIGHT_PATTERN = 1.05
    private const val WEIGHT_ANOMALY = 0.95
    private const val WEIGHT_DATA = 0.7
    private const val WEIGHT_SUMMARY = 0.8

    fun score(
        totals: WindowTotals,
        mom: MomComparison,
        points: List<PeriodTotalsPoint>,
        topCategories: List<CategoryShare>,
        detailInsights: List<LocalInsight>,
        series: ReportSeriesSnapshot? = null,
    ): List<ScoredFact> {
        val freshness = if (ReportSeriesCalculator.isExpenseExtreme(points)) 1.15 else 1.0
        val facts = mutableListOf<ScoredFact>()

        if (totals.incomeCents != 0L || totals.expenseCents != 0L) {
            val amp = ReportSeriesCalculator.normalizeDelta(
                mom.expenseDeltaCents,
                totals.expenseCents - mom.expenseDeltaCents,
            )
            if (mom.expenseDeltaCents != 0L) {
                val dir = if (mom.expenseDeltaCents > 0) "升" else "降"
                val pct = mom.expenseDeltaPercent?.let { "（${signedPercent(it)}）" }.orEmpty()
                facts += ScoredFact(
                    score = WEIGHT_MOM * amp * freshness,
                    sentence = "支出环比$dir${formatYuan(abs(mom.expenseDeltaCents))}$pct",
                    kind = LocalInsightKind.TREND,
                )
            }
            val netAmp = ReportSeriesCalculator.normalizeDelta(
                mom.netDeltaCents,
                abs(totals.incomeCents - totals.expenseCents - mom.netDeltaCents).coerceAtLeast(1L),
            )
            if (mom.netDeltaCents != 0L) {
                val dir = if (mom.netDeltaCents > 0) "改善" else "走弱"
                facts += ScoredFact(
                    score = WEIGHT_MOM * 0.9 * netAmp * freshness,
                    sentence = "净结余环比$dir${formatYuan(abs(mom.netDeltaCents))}",
                    kind = LocalInsightKind.TREND,
                )
            }
        }

        topCategories.firstOrNull()?.let { top ->
            val name = top.tagName ?: "未分类"
            val amp = (top.percent / 100.0).coerceIn(0.0, 1.0)
            facts += ScoredFact(
                score = WEIGHT_STRUCTURE * amp * freshness,
                sentence = "「$name」占支出 ${top.percent}%",
                kind = LocalInsightKind.STRUCTURE,
            )
        }

        series?.topIncreases?.firstOrNull()?.let { change ->
            val name = change.tagName ?: "未分类"
            val amp = ReportSeriesCalculator.normalizeDelta(
                change.deltaCents,
                change.previousCents.coerceAtLeast(1L),
            )
            facts += ScoredFact(
                score = WEIGHT_STRUCTURE * 1.1 * amp * freshness,
                sentence = "「$name」支出增加${formatYuan(change.deltaCents)}",
                kind = LocalInsightKind.STRUCTURE,
            )
        }

        for (insight in detailInsights) {
            val weight = when (insight.kind) {
                LocalInsightKind.BUDGET -> WEIGHT_BUDGET
                LocalInsightKind.PATTERN -> WEIGHT_PATTERN
                LocalInsightKind.ANOMALY -> WEIGHT_ANOMALY
                LocalInsightKind.DATA_QUALITY -> WEIGHT_DATA
                LocalInsightKind.SUMMARY -> WEIGHT_SUMMARY
                LocalInsightKind.STRUCTURE -> WEIGHT_STRUCTURE * 0.85
                LocalInsightKind.TREND,
                LocalInsightKind.STORY,
                LocalInsightKind.ADVICE,
                -> continue
            }
            facts += ScoredFact(
                score = weight * 0.75 * freshness,
                sentence = insight.title,
                kind = insight.kind,
            )
        }

        return facts.sortedByDescending { it.score }.take(5)
    }

    private fun signedPercent(value: Int): String =
        if (value > 0) "+$value%" else "$value%"

    private fun formatYuan(cents: Long): String {
        val yuan = cents / 100.0
        return if (yuan == yuan.toLong().toDouble()) {
            String.format(Locale.CHINA, "%.0f 元", yuan)
        } else {
            String.format(Locale.CHINA, "%.2f 元", yuan)
        }
    }
}
