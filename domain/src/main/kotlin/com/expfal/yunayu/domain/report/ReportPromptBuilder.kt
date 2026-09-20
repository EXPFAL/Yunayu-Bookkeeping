package com.expfal.yunayu.domain.report

import com.expfal.yunayu.domain.model.CategoryNoteSample
import com.expfal.yunayu.domain.report.model.CategoryShare

/** 构建报告分析提示词的纯函数对象（无副作用，便于单测）。 */
object ReportPromptBuilder {

    /** 系统指令：在已有本地结论基础上补充建议，勿重复罗列数字。 */
    fun buildSystemInstruction(): String = SYSTEM_INSTRUCTION

    /** 由收支汇总、分类占比、环比与本地洞察标题组装待分析的数据文本。 */
    fun buildDataText(
        incomeCents: Long,
        expenseCents: Long,
        topCategories: List<CategoryShare>,
        prevIncomeCents: Long,
        prevExpenseCents: Long,
        localInsightTitles: List<String> = emptyList(),
        categoryNoteSamples: List<CategoryNoteSample> = emptyList(),
    ): String = buildString {
        append("本期收入：").append(formatCents(incomeCents)).append(" 元\n")
        append("本期支出：").append(formatCents(expenseCents)).append(" 元\n")
        append("净结余：").append(formatCents(incomeCents - expenseCents)).append(" 元\n")
        if (topCategories.isEmpty()) {
            append("分类占比：本期无支出记录\n")
        } else {
            append("分类占比：\n")
            topCategories.forEach { share ->
                append("- ").append(share.tagName ?: "未分类").append("：").append(share.percent).append("%\n")
            }
        }
        append("环比：收入 上期 ").append(formatCents(prevIncomeCents)).append(" 元 → 本期 ")
            .append(formatCents(incomeCents)).append(" 元；支出 上期 ")
            .append(formatCents(prevExpenseCents)).append(" 元 → 本期 ")
            .append(formatCents(expenseCents)).append(" 元\n")
        if (localInsightTitles.isNotEmpty()) {
            append("本地已得出的结论标题（请勿逐条复述数字，请补充建议与解读）：\n")
            localInsightTitles.forEach { title ->
                append("- ").append(title).append('\n')
            }
        }
        appendCategoryNotes(categoryNoteSamples)
    }

    /** 将「分」格式化为「元」字符串，保留两位小数（如 `123456` → `"1234.56"`）。 */
    internal fun formatCents(cents: Long): String {
        val abs = if (cents < 0) -cents else cents
        val sign = if (cents < 0) "-" else ""
        return "$sign${abs / 100}.${(abs % 100).toString().padStart(2, '0')}"
    }

    /**
     * 追加「各类代表备注」小节；合计备注字符超过 [MAX_NOTE_CHARS] 时截断后续备注。
     */
    private fun StringBuilder.appendCategoryNotes(samples: List<CategoryNoteSample>) {
        val usable = samples.filter { it.notes.isNotEmpty() }
        if (usable.isEmpty()) return
        append("各类代表备注（可结合理解消费场景，勿复述数字）：\n")
        var remaining = MAX_NOTE_CHARS
        for (sample in usable) {
            if (remaining <= 0) break
            val label = sample.tagName ?: "未分类"
            val accepted = mutableListOf<String>()
            for (note in sample.notes) {
                if (remaining <= 0) break
                val take = note.take(remaining)
                if (take.isEmpty()) break
                accepted += take
                remaining -= take.length
            }
            if (accepted.isEmpty()) continue
            append("- ").append(label).append("：")
                .append(accepted.joinToString("；"))
                .append('\n')
        }
    }

    private const val MAX_NOTE_CHARS = 800

    private const val SYSTEM_INSTRUCTION =
        "你是记账应用的消费分析助手。用户侧已有本地规则洞察，请在此基础上补充建议。\n" +
            "要求：\n" +
            "1. 输出不超过 500 字的中文点评。\n" +
            "2. 不要重复罗列收入/支出/百分比等数字，侧重解读与可执行建议。\n" +
            "3. 若提供了分类代表备注，可结合备注理解消费场景，仍勿复述数字。\n" +
            "4. 纯文本输出，不要 JSON、不要代码块、不要 Markdown 标记。"
}
