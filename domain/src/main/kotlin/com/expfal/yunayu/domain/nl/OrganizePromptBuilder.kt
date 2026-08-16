package com.expfal.yunayu.domain.nl

import com.expfal.yunayu.domain.nl.model.OrganizeRecord

/** 构建整理建议引擎 systemInstruction 的纯函数对象。 */
object OrganizePromptBuilder {

    /**
     * 由待整理记录、候选标签全名清单与收入根名构建 systemInstruction。
     *
     * 提示词要求：分类判断优先看备注，金额与时间仅作辅助；`ATTACH` 的 `tag_name` 必须原样
     * 取自候选清单；`type` 为 `INCOME` 的记录只能挂/建收入体系标签；输出严格 JSON 数组并附
     * few-shot 示例。
     */
    fun build(records: List<OrganizeRecord>, candidates: List<String>, incomeRootName: String): String =
        buildString {
            append(SYSTEM_ROLE)
            append("待整理记录（record_id 必须原样回填；分类判断优先看备注，金额与时间仅作辅助）：\n")
            append(records.joinToString("\n") { recordJson(it) })
            append("\n\n")
            append("候选标签全名清单（ATTACH 的 tag_name 必须原样取自下方）：")
            append(candidates.joinToString("、").ifEmpty { NO_TAGS })
            append("\n\n")
            append(incomeRule(incomeRootName))
            append("\n")
            append(OUTPUT_RULE)
            append(EXAMPLES_PREFIX)
            append(exampleJson(incomeRootName))
        }

    /** 把单条记录序列化为一行 JSON，便于模型逐条对应；备注经 [escapeJsonString] 转义防破坏结构。 */
    private fun recordJson(record: OrganizeRecord): String =
        "{\"record_id\":${record.id},\"note\":\"${escapeJsonString(record.note.orEmpty())}\",\"amount_cents\":${record.amountCents}," +
            "\"type\":\"${record.type.name}\",\"occurred_at\":${record.occurredAt}}"

    /** JSON 字符串转义：反斜杠、双引号与常见控制字符（换行/回车/制表符及其它 < 0x20 控制符）。 */
    private fun escapeJsonString(value: String): String = buildString {
        for (char in value) {
            when (char) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> if (char.code < 0x20) {
                    append("\\u").append(char.code.toString(16).padStart(4, '0'))
                } else {
                    append(char)
                }
            }
        }
    }

    private fun incomeRule(incomeRootName: String): String =
        "收入规则：type 为 INCOME 的记录只能挂/建收入体系标签，其 tag_name 或 root_name 必须归属「$incomeRootName」体系。\n"

    private fun exampleJson(incomeRootName: String): String =
        "输入：[{\"record_id\":1,\"note\":\"买教材\",\"type\":\"EXPENSE\"},{\"record_id\":2,\"note\":\"兼职发传单\",\"type\":\"INCOME\"}]\n" +
            "输出：[{\"record_id\":1,\"action\":\"ATTACH\",\"tag_name\":\"学习\"}," +
            "{\"record_id\":2,\"action\":\"CREATE\",\"tag_name\":\"兼职\",\"root_name\":\"$incomeRootName\"}]\n"

    private const val SYSTEM_ROLE =
        "你是记账应用的分类整理助手。为每一条未分类交易给出一个标签建议，只输出 JSON 数组，不要输出任何其它说明文字。\n\n"

    private const val NO_TAGS = "无"

    private const val OUTPUT_RULE =
        "输出规则：\n" +
            "- 输出为 JSON 数组，每元素一个对象：{\"record_id\":N,\"action\":\"ATTACH\"|\"CREATE\",\"tag_name\":\"...\",\"root_name\":\"...\"?}\n" +
            "- action=ATTACH：tag_name 原样取自候选标签全名，root_name 省略\n" +
            "- action=CREATE：在根类下新建标签，tag_name 为 2~6 字新标签名，root_name 为所属根类名\n" +
            "- 没有把握的记录不要输出\n"

    private const val EXAMPLES_PREFIX = "示例：\n"
}
