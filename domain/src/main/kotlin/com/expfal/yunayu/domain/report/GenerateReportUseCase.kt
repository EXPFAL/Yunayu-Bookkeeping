package com.expfal.yunayu.domain.report

import com.expfal.yunayu.domain.model.WindowTotals
import com.expfal.yunayu.domain.report.model.CategoryShare
import com.expfal.yunayu.domain.report.model.Report
import com.expfal.yunayu.domain.report.model.ReportPeriodType
import com.expfal.yunayu.domain.report.model.ReportStatus
import com.expfal.yunayu.domain.repository.ReportRepository
import com.expfal.yunayu.domain.repository.TransactionRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull

/**
 * 生成一份月度 / 年度报告的编排用例。
 *
 * 链路：聚合当期与上期收支 + 当期支出分类占比 → 组提示词 → 引擎可用性检查 → 分析（超时兜底）
 * → 长文本容错 → 落库。引擎不可用 / 失败 / 超时仅置 [ReportStatus.FAILED] 且保留结构化数据，
 * 不抛异常；仅 [CancellationException] 向上重抛。
 */
class GenerateReportUseCase(
    private val transactionRepository: TransactionRepository,
    private val reportRepository: ReportRepository,
    private val analyzer: ReportAnalyzer,
) {

    /**
     * 生成并持久化报告；[prevWindowStartMs]/[prevWindowEndMs] 为环比基期窗口（半开区间）。
     */
    suspend operator fun invoke(
        periodType: ReportPeriodType,
        periodKey: String,
        windowStartMs: Long,
        windowEndMs: Long,
        prevWindowStartMs: Long,
        prevWindowEndMs: Long,
        generatedAtMs: Long = System.currentTimeMillis(),
    ) {
        val totals = transactionRepository.getWindowTotals(windowStartMs, windowEndMs)
        val prevTotals = transactionRepository.getWindowTotals(prevWindowStartMs, prevWindowEndMs)
        val topCategories = buildTopCategories(windowStartMs, windowEndMs, totals.expenseCents)

        val analysis = analyze(topCategories, totals, prevTotals)
        reportRepository.upsert(
            Report(
                periodType = periodType,
                periodKey = periodKey,
                windowStartMs = windowStartMs,
                windowEndMs = windowEndMs,
                incomeCents = totals.incomeCents,
                expenseCents = totals.expenseCents,
                topCategories = topCategories,
                prevIncomeCents = prevTotals.incomeCents,
                prevExpenseCents = prevTotals.expenseCents,
                analysisText = analysis.text,
                status = if (analysis.succeeded) ReportStatus.SUCCESS else ReportStatus.FAILED,
                generatedAtMs = generatedAtMs,
            ),
        )
    }

    /** 取当期支出按金额降序前 [TOP_CATEGORIES_LIMIT] 个分类，换算占比。 */
    private suspend fun buildTopCategories(
        windowStartMs: Long,
        windowEndMs: Long,
        expenseCents: Long,
    ): List<CategoryShare> =
        transactionRepository.getExpenseByCategory(windowStartMs, windowEndMs)
            .take(TOP_CATEGORIES_LIMIT)
            .map { CategoryShare(it.tagName, it.cents, percentOf(it.cents, expenseCents)) }

    /**
     * 调用引擎分析：不可用 / 超时 / 失败视为未成功（`succeeded = false`，文本 `null`）；
     * 成功但输出空白时视为成功且文本置 `null`（结构化数据仍在）。
     */
    private suspend fun analyze(
        topCategories: List<CategoryShare>,
        totals: WindowTotals,
        prevTotals: WindowTotals,
    ): Analysis {
        if (!analyzer.isAvailable()) return Analysis.failed()

        val instruction = ReportPromptBuilder.buildSystemInstruction()
        val dataText = ReportPromptBuilder.buildDataText(
            incomeCents = totals.incomeCents,
            expenseCents = totals.expenseCents,
            topCategories = topCategories,
            prevIncomeCents = prevTotals.incomeCents,
            prevExpenseCents = prevTotals.expenseCents,
        )
        val raw = try {
            withTimeoutOrNull(ANALYZE_TIMEOUT_MILLIS) { analyzer.analyze(instruction, dataText) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        } ?: return Analysis.failed()

        return Analysis(succeeded = true, text = sanitizeAnalysis(raw))
    }

    /**
     * 长文本容错：去 Markdown 代码围栏、trim、截断至 [MAX_ANALYSIS_CHARS]；若截断点落在高位代理
     * （surrogate）则回退一位避免拆碎代理对；空白返回 `null`。
     */
    private fun sanitizeAnalysis(raw: String): String? {
        var text = raw.trim()
        text = text.replace(Regex("^```[a-zA-Z]*\\s*"), "").trim()
        text = text.removeSuffix("```").trim()
        text = text.take(MAX_ANALYSIS_CHARS)
        if (text.isNotEmpty() && Character.isHighSurrogate(text.last())) {
            text = text.dropLast(1)
        }
        return text.takeIf { it.isNotBlank() }
    }

    private fun percentOf(partCents: Long, totalCents: Long): Int =
        if (totalCents <= 0L) 0 else ((partCents * 100) / totalCents).toInt()

    /** 分析结果：是否成功 + 分析文本（成功但空白时文本为 `null`）。 */
    private data class Analysis(
        val succeeded: Boolean,
        val text: String?,
    ) {
        companion object {
            fun failed(): Analysis = Analysis(succeeded = false, text = null)
        }
    }

    private companion object {
        /** 分类占比最多保留的分类数。 */
        const val TOP_CATEGORIES_LIMIT = 5

        /**
         * 分析超时（毫秒），超时视为失败。作为协程层兜底：连接（10s）+ 读取（30s）全链路并留余量，
         * readTimeout 才是真正上界，协程超时仅兜底阻塞读不响应协程取消的极端场景。
         */
        const val ANALYZE_TIMEOUT_MILLIS = 65_000L

        /** 分析文本长度上限（字符）。 */
        const val MAX_ANALYSIS_CHARS = 2000
    }
}
