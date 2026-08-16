package com.expfal.yunayu.domain.nl

import com.expfal.yunayu.domain.nl.model.TagPairInfo

/** 构建标签合并候选引擎 systemInstruction 的纯函数对象。 */
object MergeCandidatePromptBuilder {

    /**
     * 由标签对清单构建 systemInstruction。
     *
     * 提示词要求：逐对判定两个标签是否语义重复；`tag_a` / `tag_b` 必须原样回填为输入中的
     * 标签名；`decision` 三选一（`A_INTO_B` 丢弃 A 保留 B、`B_INTO_A` 丢弃 B 保留 A、
     * `KEEP_BOTH` 两者保留）；输出严格 JSON 数组并附 1 条 few-shot 示例。
     */
    fun build(pairs: List<TagPairInfo>): String =
        buildString {
            append(SYSTEM_ROLE)
            append("待判定标签对（tag_a 与 tag_b 必须原样取自下方，不要改写名称）：\n")
            append(pairs.joinToString("\n") { pairJson(it) })
            append("\n\n")
            append(OUTPUT_RULE)
            append(EXAMPLES_PREFIX)
            append(EXAMPLE)
        }

    /** 把一对标签序列化为一行 JSON，便于模型逐对对应；标签名经 [escapeJsonString] 转义防破坏结构。 */
    private fun pairJson(pair: TagPairInfo): String =
        "{\"tag_a\":\"${escapeJsonString(pair.nameA)}\",\"tag_b\":\"${escapeJsonString(pair.nameB)}\",\"count_a\":${pair.countA}," +
            "\"count_b\":${pair.countB}}"

    /** JSON 字符串转义：反斜杠、双引号与常见控制字符。 */
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

    private const val SYSTEM_ROLE =
        "你是记账应用的标签治理助手。判断每对标签是否为同一语义的重复标签，只输出 JSON 数组，" +
            "不要输出任何其它说明文字。\n\n"

    private const val OUTPUT_RULE =
        "输出规则：\n" +
            "- 输出为 JSON 数组，每元素一个对象：{\"tag_a\":\"...\",\"tag_b\":\"...\",\"decision\":\"...\"}\n" +
            "- tag_a / tag_b 必须原样回填输入中的标签名\n" +
            "- decision 取值：A_INTO_B（A 合并进 B，保留 B）、B_INTO_A（B 合并进 A，保留 A）、" +
            "KEEP_BOTH（两者语义不同，都保留）\n" +
            "- 语义相同或高度重叠（如「餐饮」与「吃饭」）才判为合并；把握不足的一律 KEEP_BOTH\n"

    private const val EXAMPLES_PREFIX = "示例：\n"

    private const val EXAMPLE =
        "输入：[{\"tag_a\":\"餐饮\",\"tag_b\":\"吃饭\",\"count_a\":12,\"count_b\":3}]\n" +
            "输出：[{\"tag_a\":\"餐饮\",\"tag_b\":\"吃饭\",\"decision\":\"A_INTO_B\"}]\n"
}
