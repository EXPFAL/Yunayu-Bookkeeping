package com.expfal.yunayu.domain.nl

import com.expfal.yunayu.domain.model.TransactionType

/** 构建「按备注猜标签」systemInstruction 的纯函数。 */
object SuggestTagsFromNotePromptBuilder {

    /**
     * @param note 用户备注
     * @param type 收支方向
     * @param candidates 候选标签全名（根 / 根·子）
     */
    fun build(note: String, type: TransactionType, candidates: List<String>): String = buildString {
        append(SYSTEM_ROLE)
        append("交易类型：").append(type.name).append('\n')
        append("用户备注：").append(note.trim()).append('\n')
        append("候选标签全名清单（tag_name 必须原样取自下方；没有把握则输出空数组）：")
        append(candidates.joinToString("、").ifEmpty { "无" })
        append("\n\n")
        append(OUTPUT_RULE)
        append(EXAMPLE)
    }

    private const val SYSTEM_ROLE =
        "你是记账应用的分类助手。根据用户备注，从候选标签中选出最可能的 1～3 个标签。" +
            "只输出 JSON 数组，不要输出任何其它说明文字。\n\n"

    private const val OUTPUT_RULE =
        "输出规则：\n" +
            "- 输出 JSON 数组，每元素 {\"tag_name\":\"...\"}，最多 3 个\n" +
            "- tag_name 必须原样取自候选清单\n" +
            "- 没有把握时输出 []\n" +
            "- 不要 CREATE 新标签\n"

    private const val EXAMPLE =
        "示例：备注「买教材」候选含「学习·课本教辅」→ [{\"tag_name\":\"学习·课本教辅\"}]\n"
}
