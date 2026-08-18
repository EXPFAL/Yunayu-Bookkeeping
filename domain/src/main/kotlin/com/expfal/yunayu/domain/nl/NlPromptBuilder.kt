package com.expfal.yunayu.domain.nl

/** 构建解析引擎 systemInstruction 的纯函数对象。 */
object NlPromptBuilder {

    /** 由有界标签候选集构建精简的 systemInstruction（schema + few-shot + 候选标签）。 */
    fun build(tags: List<String>): String = buildString {
        append(SYSTEM_ROLE)
        append(SCHEMA_SECTION)
        append(TAG_PREFIX)
        append(tags.joinToString("、").ifEmpty { NO_TAGS })
        append("\n\n")
        append(EXAMPLES_SECTION)
    }

    private const val SYSTEM_ROLE =
        "解析中文记账为JSON，只输出JSON。\n"

    private const val SCHEMA_SECTION =
        "字段：amount(元字符串)、type(expense/income默认expense)、tag(从候选选一个可省略)、note(2-8字核心短语必填)、date(今天/昨天/前天/YYYY-MM-DD默认今天)\n"

    private const val TAG_PREFIX = "候选："

    private const val NO_TAGS = "无"

    private const val EXAMPLES_SECTION =
        "午饭花了20块→{\"amount\":\"20\",\"type\":\"expense\",\"tag\":\"生活·餐饮\",\"note\":\"午饭\",\"date\":\"今天\"}\n" +
            "收到奖学金2000→{\"amount\":\"2000\",\"type\":\"income\",\"tag\":\"学习\",\"note\":\"奖学金\",\"date\":\"今天\"}"
}
