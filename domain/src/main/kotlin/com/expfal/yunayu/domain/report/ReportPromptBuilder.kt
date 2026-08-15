package com.expfal.yunayu.domain.report

import com.expfal.yunayu.domain.report.model.CategoryShare

/** 构建报告分析提示词的纯函数对象（无副作用，便于单测）。 */
object ReportPromptBuilder {

    /** 固定系统指令：要求 ≤500 字中文分析、消费洞察 + 建议、纯文本输出、禁 JSON/代码块。 */
    fun buildSystemInstruction(): String = SYSTEM_INSTRUCTION

    /** 由收支汇总、分类占比与环比数据组装待分析的数据文本。 */
    fun buildDataText(
        incomeCents: Long,
        expenseCents: Long,
        topCategories: List<CategoryShare>,
        prevIncomeCents: Long,
        prevExpenseCents: Long,
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
            .append(formatCents(expenseCents)).append(" 元")
    }

    /** 将「分」格式化为「元」字符串，保留两位小数（如 `123456` → `"1234.56"`）。 */
    internal fun formatCents(cents: Long): String {
        val abs = if (cents < 0) -cents else cents
        val sign = if (cents < 0) "-" else ""
        return "$sign${abs / 100}.${(abs % 100).toString().padStart(2, '0')}"
    }

    private const val SYSTEM_INSTRUCTION =
        "你是记账应用的消费分析助手。根据用户提供的记账统计数据进行消费分析。\n" +
            "要求：\n" +
            "1. 输出不超过 500 字的中文分析。\n" +
            "2. 包含消费洞察（主要消费方向、结构变化）与实用建议。\n" +
            "3. 纯文本输出，不要 JSON、不要代码块、不要 Markdown 标记。"
}
