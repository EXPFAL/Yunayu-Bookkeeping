package com.expfal.yunayu.domain.nl

/** 解析「按备注猜标签」模型输出为标签全名列表（最多 [maxCount]）。 */
object SuggestTagsFromNoteOutputParser {

    fun parse(raw: String, validNames: Set<String>, maxCount: Int = 3): List<String> {
        val text = raw.trim()
        if (text.isEmpty()) return emptyList()
        val start = text.indexOf('[')
        val end = text.lastIndexOf(']')
        if (start < 0 || end <= start) return emptyList()
        val normalizedValid = validNames.associateBy { normalize(it) }
        return splitTopLevelObjects(text.substring(start, end + 1))
            .mapNotNull { extractStringField(it, "tag_name")?.trim()?.takeIf { n -> n.isNotEmpty() } }
            .mapNotNull { name -> normalizedValid[normalize(name)] }
            .distinct()
            .take(maxCount)
    }

    private fun normalize(text: String): String = text.filterNot { it.isWhitespace() }

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

    private fun extractStringField(json: String, key: String): String? {
        val pattern = "\"$key\"\\s*:\\s*\""
        val idx = Regex(pattern).find(json)?.range?.last?.plus(1) ?: return null
        val out = StringBuilder()
        var i = idx
        var escaped = false
        while (i < json.length) {
            val c = json[i]
            when {
                escaped -> {
                    out.append(c)
                    escaped = false
                }
                c == '\\' -> escaped = true
                c == '"' -> return out.toString()
                else -> out.append(c)
            }
            i++
        }
        return null
    }
}
