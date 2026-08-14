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
        "你是记账应用的意图解析器。把用户的中文记账描述解析为一个 JSON 对象，只输出 JSON，不要输出任何其它说明文字。\n\n"

    private const val SCHEMA_SECTION =
        "JSON 字段：\n" +
            "- \"amount\"：金额，纯数字字符串，单位元（如 \"20\"、\"35.5\"）\n" +
            "- \"type\"：\"expense\" 或 \"income\"，缺省 expense\n" +
            "- \"tag\"：标签短语，只能从下方候选标签中选一个，没有把握就省略\n" +
            "- \"note\"：备注短语，可省略\n" +
            "- \"date\"：\"今天\"、\"昨天\"、\"前天\" 或 \"YYYY-MM-DD\"，缺省当作今天\n\n"

    private const val TAG_PREFIX = "候选标签："

    private const val NO_TAGS = "无"

    private const val EXAMPLES_SECTION =
        "示例：\n" +
            "输入：午饭花了20块\n" +
            "输出：{\"amount\":\"20\",\"type\":\"expense\",\"tag\":\"生活·餐饮\",\"note\":\"午饭\",\"date\":\"今天\"}\n" +
            "输入：昨天买教材35.5\n" +
            "输出：{\"amount\":\"35.5\",\"type\":\"expense\",\"tag\":\"学习\",\"note\":\"买教材\",\"date\":\"昨天\"}\n" +
            "输入：收到奖学金2000\n" +
            "输出：{\"amount\":\"2000\",\"type\":\"income\",\"tag\":\"学习\",\"note\":\"奖学金\",\"date\":\"今天\"}\n" +
            "输入：打车12\n" +
            "输出：{\"amount\":\"12\",\"type\":\"expense\",\"tag\":\"生活·交通\",\"note\":\"打车\",\"date\":\"今天\"}"
}
