package com.expfal.yunayu.domain.nl

import com.expfal.yunayu.domain.model.TransactionType
import com.expfal.yunayu.domain.nl.model.NlTransactionDraft

/** 把模型原始输出解析为归一化交易草稿的纯函数。 */
object NlOutputParser {

    /**
     * 从模型原始文本中抽取首个 `{` 到末个 `}` 的 JSON 片段，逐字段容错解析。
     *
     * 金额为关键字段：缺失或非法时返回 `null`；标签短语/备注/日期为可选字段，日期缺失或
     * 非法时回退为 [nowEpochMillis]；`type` 缺省为 `EXPENSE`。当模型未输出 `note` 时，
     * 用 [originalInput] 经 [NlNoteFallback] 本地兜底生成备注；[originalInput] 缺省为
     * `null`（不兜底），保持两参调用兼容。最终 `note`（无论模型产出还是兜底）统一经
     * [NlNoteFallback.truncateNote] 截断至 ≤8 字，避免 UTF-16 孤立代理。
     */
    fun parseToDraft(
        rawOutput: String?,
        nowEpochMillis: Long,
        originalInput: String? = null,
    ): NlTransactionDraft? {
        val raw = rawOutput?.trim().orEmpty()
        if (raw.isEmpty()) return null
        val start = raw.indexOf('{')
        val end = raw.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        val json = raw.substring(start, end + 1)

        val amountText = extractField(json, KEY_AMOUNT) ?: return null
        val amountCents = NlAmountDate.parseAmountToCents(amountText) ?: return null

        val type = resolveType(extractField(json, KEY_TYPE))
        val tagPhrase = extractField(json, KEY_TAG)?.trim()?.takeIf { it.isNotEmpty() }
        val modelNote = extractField(json, KEY_NOTE)?.trim()?.takeIf { it.isNotEmpty() }
        val note = (modelNote ?: NlNoteFallback.extractNote(originalInput, tagPhrase))
            ?.let { NlNoteFallback.truncateNote(it) }
        val occurredAt = NlAmountDate.parseOccurredAtEpochMillis(
            extractField(json, KEY_DATE),
            nowEpochMillis,
        ) ?: nowEpochMillis

        return NlTransactionDraft(
            amountCents = amountCents,
            type = type,
            tagPhrase = tagPhrase,
            note = note,
            occurredAtEpochMillis = occurredAt,
        )
    }

    /** 从扁平 JSON 对象中抽取指定 key 的字符串/数字字段值；缺失返回 `null`。 */
    private fun extractField(json: String, key: String): String? {
        val match = fieldPattern(key).find(json) ?: return null
        return match.groupValues.getOrNull(1)?.takeIf { it.isNotEmpty() }
            ?: match.groupValues.getOrNull(2)?.trim()
    }

    /** 匹配 `"key": "..."` 或 `"key": 数字/裸值` 的字段抽取正则。 */
    private fun fieldPattern(key: String): Regex =
        Regex("\"$key\"\\s*:\\s*(?:\"([^\"]*)\"|([^,}]+))")

    /** 识别收入标记（`income`/`收入`），其余一律按支出处理。 */
    private fun resolveType(typeText: String?): TransactionType {
        val lowered = typeText?.trim()?.lowercase() ?: return TransactionType.EXPENSE
        return if (lowered.contains("income") || lowered.contains("收入")) {
            TransactionType.INCOME
        } else {
            TransactionType.EXPENSE
        }
    }

    private const val KEY_AMOUNT = "amount"
    private const val KEY_TYPE = "type"
    private const val KEY_TAG = "tag"
    private const val KEY_NOTE = "note"
    private const val KEY_DATE = "date"
}
