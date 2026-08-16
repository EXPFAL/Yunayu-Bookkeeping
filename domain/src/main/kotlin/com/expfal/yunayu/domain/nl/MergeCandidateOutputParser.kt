package com.expfal.yunayu.domain.nl

import com.expfal.yunayu.domain.model.MergeDecision

/**
 * 把模型原始输出解析为标签合并候选决策的纯函数。
 *
 * 与 [OrganizeOutputParser] 同构：抽取最外层 JSON 数组，逐对象容错解析；单条跳过条件为
 * `tag_a` / `tag_b` 缺失或空白、`decision` 非法、或标签对不在 [validPairs] 中。
 */
object MergeCandidateOutputParser {

    /** 一条解析成功的标签对决策。 */
    data class MergePairDecision(
        val tagA: String,
        val tagB: String,
        val decision: MergeDecision,
    )

    /**
     * 从模型原始文本中抽取最外层 JSON 数组（首个 `[` 到末个 `]`），逐对象容错解析。
     *
     * [validPairs] 为本次请求发送的合法标签对集合（`tagA to tagB` 有序）；仅当输出的
     * `(tag_a, tag_b)` 命中该集合且 `decision` 合法时才保留，任一对象解析失败不阻塞整批。
     */
    fun parse(raw: String, validPairs: Set<Pair<String, String>>): List<MergePairDecision> {
        val text = raw.trim()
        if (text.isEmpty()) return emptyList()
        val start = text.indexOf('[')
        val end = text.lastIndexOf(']')
        if (start < 0 || end <= start) return emptyList()
        // 双层防线之一：LLM 重复输出常见，按标签对去重（首个优先）。
        return splitTopLevelObjects(text.substring(start, end + 1))
            .mapNotNull { parseItem(it, validPairs) }
            .distinctBy { it.tagA to it.tagB }
    }

    /** 按数组最外层的逗号拆分对象（跳过字符串内的逗号与嵌套括号）。 */
    private fun splitTopLevelObjects(array: String): List<String> {
        val result = mutableListOf<String>()
        val current = StringBuilder()
        var depth = 0
        var inString = false
        var escaped = false
        for (char in array) {
            if (inString) {
                current.append(char)
                when {
                    escaped -> escaped = false
                    char == '\\' -> escaped = true
                    char == '"' -> inString = false
                }
                continue
            }
            when (char) {
                '"' -> {
                    inString = true
                    current.append(char)
                }
                '{', '[' -> {
                    depth++
                    current.append(char)
                }
                '}', ']' -> {
                    depth--
                    current.append(char)
                }
                ',' -> {
                    if (depth == 1) {
                        if (current.isNotBlank()) result += current.toString()
                        current.clear()
                    } else {
                        current.append(char)
                    }
                }
                else -> current.append(char)
            }
        }
        if (current.isNotBlank()) result += current.toString()
        return result
    }

    /** 解析单个对象为决策；非法时返回 `null`（跳过）。 */
    private fun parseItem(
        item: String,
        validPairs: Set<Pair<String, String>>,
    ): MergePairDecision? {
        val tagA = extractStringField(item, KEY_TAG_A)?.trim()?.takeIf { it.isNotEmpty() }
            ?: return null
        val tagB = extractStringField(item, KEY_TAG_B)?.trim()?.takeIf { it.isNotEmpty() }
            ?: return null
        val decisionText = extractStringField(item, KEY_DECISION)?.trim() ?: return null
        val decision = when (decisionText.uppercase()) {
            MergeDecision.KEEP_BOTH.name -> MergeDecision.KEEP_BOTH
            // 提示词要求模型输出短码 A_INTO_B / B_INTO_A，此处同时兼容短码与枚举长名，避免误拒。
            "A_INTO_B", MergeDecision.MERGE_A_INTO_B.name -> MergeDecision.MERGE_A_INTO_B
            "B_INTO_A", MergeDecision.MERGE_B_INTO_A.name -> MergeDecision.MERGE_B_INTO_A
            else -> return null
        }
        if (tagA to tagB !in validPairs) return null
        return MergePairDecision(tagA, tagB, decision)
    }

    /** 抽取 `"key": "..."` 字符串字段值，处理 `\"` 与 `\\` 转义；缺失返回 `null`。 */
    private fun extractStringField(item: String, key: String): String? {
        val match = Regex("\"$key\"\\s*:\\s*\"").find(item) ?: return null
        val valueStart = match.range.last + 1
        val builder = StringBuilder()
        var index = valueStart
        while (index < item.length) {
            val char = item[index]
            if (char == '\\' && index + 1 < item.length) {
                builder.append(item[index + 1])
                index += 2
                continue
            }
            if (char == '"') break
            builder.append(char)
            index++
        }
        return builder.toString()
    }

    private const val KEY_TAG_A = "tag_a"
    private const val KEY_TAG_B = "tag_b"
    private const val KEY_DECISION = "decision"
}
