package com.expfal.yunayu.domain.nl

import com.expfal.yunayu.domain.nl.model.TagSuggestion

/** 把模型原始输出解析为新标签建议的纯函数。 */
object TagSuggestionOutputParser {

    /**
     * 从原始文本中抽取首个 `{` 到末个 `}` 的 JSON 片段，逐字段容错解析。
     * `tag_name` 为空或空白、`root` 不在 [candidateRoots] 候选集时返回 `null`。
     */
    fun parse(raw: String, candidateRoots: List<String>): TagSuggestion? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null
        val start = trimmed.indexOf('{')
        val end = trimmed.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        val json = trimmed.substring(start, end + 1)

        val tagName = extractField(json, KEY_TAG_NAME)
            ?.let { unescape(it) }
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: return null
        val root = extractField(json, KEY_ROOT)
            ?.let { unescape(it) }
            ?.trim()
            ?: return null
        if (root !in candidateRoots) return null
        return TagSuggestion(tagName = tagName, rootName = root)
    }

    /** 从扁平 JSON 对象中抽取指定 key 的字符串/数字字段值；缺失返回 `null`。 */
    private fun extractField(json: String, key: String): String? {
        val match = fieldPattern(key).find(json) ?: return null
        return match.groupValues.getOrNull(1)?.takeIf { it.isNotEmpty() }
            ?: match.groupValues.getOrNull(2)?.trim()
    }

    /** 匹配 `"key": "..."` 或 `"key": 数字/裸值` 的字段抽取正则；字符串组支持 `\"` 与 `\\` 转义。 */
    private fun fieldPattern(key: String): Regex =
        Regex("\"$key\"\\s*:\\s*(?:\"((?:[^\"\\\\]|\\\\.)*)\"|([^,}]+))")

    /** 基本反转义：`\"` → `"`、`\\` → `\`，覆盖模型输出常见转义场景。 */
    private fun unescape(value: String): String =
        value.replace("\\\"", "\"").replace("\\\\", "\\")

    private const val KEY_TAG_NAME = "tag_name"
    private const val KEY_ROOT = "root"
}
