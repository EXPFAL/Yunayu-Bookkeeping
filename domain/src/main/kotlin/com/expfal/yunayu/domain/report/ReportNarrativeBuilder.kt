package com.expfal.yunayu.domain.report

import com.expfal.yunayu.domain.model.WindowTotals
import com.expfal.yunayu.domain.report.model.LocalInsight
import com.expfal.yunayu.domain.report.model.LocalInsightKind
import java.util.Locale

/**
 * 本地叙事合成器：总览 + 主线发现 + 可选收尾建议 → [LocalInsightKind.STORY]。
 */
object ReportNarrativeBuilder {

    private const val MIN_CHARS = 80
    private const val MAX_CHARS = 180

    fun build(
        totals: WindowTotals,
        scoredFacts: List<ScoredFact>,
        adviceTitles: List<String>,
    ): LocalInsight {
        if (totals.incomeCents == 0L && totals.expenseCents == 0L) {
            return LocalInsight(
                kind = LocalInsightKind.STORY,
                title = "本期故事",
                detail = "本期暂无收支记录，记几笔后再来看复盘会更有参考价值。",
            )
        }
        val net = totals.incomeCents - totals.expenseCents
        val tone = when {
            net > 0L -> "结余偏稳"
            net < 0L -> "支出偏紧"
            else -> "收支持平"
        }
        val overview = "本期收入${formatYuan(totals.incomeCents)}、支出${formatYuan(totals.expenseCents)}，" +
            "净结余${formatYuan(net)}，$tone。"
        val discoveries = scoredFacts.take(2).joinToString("；") { it.sentence }
        val discoveryPart = if (discoveries.isNotEmpty()) {
            "主线上看，$discoveries。"
        } else {
            ""
        }
        val advicePart = adviceTitles.firstOrNull()?.let { "下期可优先：$it。" }.orEmpty()
        var detail = (overview + discoveryPart + advicePart).trim()
        if (detail.length < MIN_CHARS && scoredFacts.size > 2) {
            val extra = scoredFacts.drop(2).take(2).joinToString("；") { it.sentence }
            if (extra.isNotEmpty()) {
                detail = (detail.removeSuffix("。") + "；另见$extra。").trim()
            }
        }
        if (detail.length > MAX_CHARS) {
            detail = detail.take(MAX_CHARS - 1) + "…"
        }
        return LocalInsight(
            kind = LocalInsightKind.STORY,
            title = "本期故事",
            detail = detail,
        )
    }

    private fun formatYuan(cents: Long): String {
        val yuan = cents / 100.0
        return if (yuan == yuan.toLong().toDouble()) {
            String.format(Locale.CHINA, "%.0f 元", yuan)
        } else {
            String.format(Locale.CHINA, "%.2f 元", yuan)
        }
    }
}
