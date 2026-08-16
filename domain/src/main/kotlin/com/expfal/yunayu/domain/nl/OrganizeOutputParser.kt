package com.expfal.yunayu.domain.nl

import com.expfal.yunayu.domain.nl.model.Action
import com.expfal.yunayu.domain.nl.model.OrganizeSuggestion

/** 把模型原始输出解析为整理建议列表的纯函数。 */
object OrganizeOutputParser {

    /**
     * 从模型原始文本中抽取最外层 JSON 数组（首个 `[` 到末个 `]`），逐对象容错解析。
     *
     * 单条跳过条件：字段缺失、`record_id` 不在 [validRecordIds]、`action` 非法或
     * `tag_name` 为空；任一对象解析失败不阻塞整批。字符串字段支持 `\"` 转义引号容错。
     */
    fun parse(raw: String, validRecordIds: Set<Long>): List<OrganizeSuggestion> {
        val text = raw.trim()
        if (text.isEmpty()) return emptyList()
        val start = text.indexOf('[')
        val end = text.lastIndexOf(']')
        if (start < 0 || end <= start) return emptyList()
        // LLM 重复输出常见：按 record_id 去重（首个优先），避免下游 LazyColumn 重复 key 崩溃。
        return splitTopLevelObjects(text.substring(start, end + 1))
            .mapNotNull { parseItem(it, validRecordIds) }
            .distinctBy { it.recordId }
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

    /** 解析单个对象为建议；非法时返回 `null`（跳过）。 */
    private fun parseItem(item: String, validRecordIds: Set<Long>): OrganizeSuggestion? {
        val recordId = extractLongField(item, KEY_RECORD_ID) ?: return null
        if (recordId !in validRecordIds) return null
        val actionText = extractStringField(item, KEY_ACTION)?.trim() ?: return null
        val action = when (actionText.uppercase()) {
            Action.ATTACH.name -> Action.ATTACH
            Action.CREATE.name -> Action.CREATE
            else -> return null
        }
        val tagName = extractStringField(item, KEY_TAG_NAME)?.trim()?.takeIf { it.isNotEmpty() }
            ?: return null
        val rootName = extractStringField(item, KEY_ROOT_NAME)?.trim()?.takeIf { it.isNotEmpty() }
        return OrganizeSuggestion(recordId, action, tagName, rootName)
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

    /** 抽取 `"key": 数字` 整型字段值；缺失或非法返回 `null`。 */
    private fun extractLongField(item: String, key: String): Long? {
        val match = Regex("\"$key\"\\s*:\\s*(-?\\d+)").find(item) ?: return null
        return match.groupValues[1].toLongOrNull()
    }

    private const val KEY_RECORD_ID = "record_id"
    private const val KEY_ACTION = "action"
    private const val KEY_TAG_NAME = "tag_name"
    private const val KEY_ROOT_NAME = "root_name"
}
