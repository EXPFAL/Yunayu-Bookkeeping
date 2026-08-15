package com.expfal.yunayu.domain.nl

/** 构建新标签建议引擎 systemInstruction 的纯函数对象。 */
object TagSuggestionPromptBuilder {

    /** 由用户输入短语与候选根类名构建精简的 systemInstruction（角色 + schema + few-shot + 候选根类）。 */
    fun build(input: String, candidateRoots: List<String>): String = buildString {
        append(SYSTEM_ROLE)
        append(SCHEMA_SECTION)
        append(ROOT_PREFIX)
        append(candidateRoots.joinToString("、").ifEmpty { NO_ROOTS })
        append("\n\n")
        append(EXAMPLES_SECTION)
        append("\n输入：")
        append(input)
        append("\n输出：")
    }

    private const val SYSTEM_ROLE =
        "你是记账应用的标签助手。用户正在记一笔账，但没有匹配到现有标签。请建议一个简洁的新标签名，并从候选根类中选一个最合适的归属。只输出 JSON，不要输出任何其它说明文字。\n\n"

    private const val SCHEMA_SECTION =
        "JSON 字段：\n" +
            "- \"tag_name\"：新标签名，2~8 字中文短语（如 \"咖啡\"、\"球鞋\"）\n" +
            "- \"root\"：所属根类，只能从下方候选根类中选一个\n\n"

    private const val ROOT_PREFIX = "候选根类："

    private const val NO_ROOTS = "无"

    private const val EXAMPLES_SECTION =
        "示例：\n" +
            "输入：每天买咖啡\n" +
            "输出：{\"tag_name\":\"咖啡\",\"root\":\"生活\"}\n" +
            "输入：报名驾校\n" +
            "输出：{\"tag_name\":\"驾校\",\"root\":\"生活\"}\n" +
            "输入：买网球拍\n" +
            "输出：{\"tag_name\":\"网球拍\",\"root\":\"娱乐\"}"
}
