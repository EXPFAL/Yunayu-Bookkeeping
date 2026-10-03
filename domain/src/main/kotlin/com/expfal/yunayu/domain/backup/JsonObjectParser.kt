package com.expfal.yunayu.domain.backup

/**
 * 极简 JSON 对象/数组解析器，仅支撑账本备份所需的字面量与一层数组。
 * 非通用 JSON 库；非法输入抛 [IllegalArgumentException]。
 */
internal object JsonObjectParser {

    sealed interface Value {
        fun asObject(): Obj = this as? Obj ?: error("expected object")
        fun asStringOrNull(): String? = (this as? Str)?.value
    }

    data class Obj(val map: Map<String, Value>) : Value {
        fun string(key: String): String? = (map[key] as? Str)?.value
        fun stringOrNull(key: String): String? = when (val v = map[key]) {
            null, Null -> null
            is Str -> v.value
            else -> error("expected string for $key")
        }
        fun long(key: String): Long? = (map[key] as? Num)?.value
        fun longOrNull(key: String): Long? = when (val v = map[key]) {
            null, Null -> null
            is Num -> v.value
            else -> error("expected number/null for $key")
        }
        fun int(key: String): Int? = long(key)?.toInt()
        fun bool(key: String): Boolean? = (map[key] as? Bool)?.value
        fun array(key: String): List<Value> = (map[key] as? Arr)?.items ?: emptyList()
    }

    data class Arr(val items: List<Value>) : Value
    data class Str(val value: String) : Value
    data class Num(val value: Long) : Value
    data class Bool(val value: Boolean) : Value
    data object Null : Value

    fun parseObject(json: String): Obj {
        val parser = Parser(json)
        val value = parser.parseValue()
        parser.skipWs()
        if (!parser.eof()) error("trailing junk")
        return value.asObject()
    }

    private class Parser(private val src: String) {
        private var i = 0

        fun eof(): Boolean = i >= src.length

        fun skipWs() {
            while (i < src.length && src[i].isWhitespace()) i++
        }

        fun parseValue(): Value {
            skipWs()
            if (eof()) error("unexpected end")
            return when (src[i]) {
                '{' -> parseObject()
                '[' -> parseArray()
                '"' -> Str(parseString())
                't' -> parseLiteral("true", Bool(true))
                'f' -> parseLiteral("false", Bool(false))
                'n' -> parseLiteral("null", Null)
                else -> {
                    if (src[i] == '-' || src[i].isDigit()) {
                        Num(parseNumber())
                    } else {
                        error("unexpected '${src[i]}' at $i")
                    }
                }
            }
        }

        private fun parseObject(): Obj {
            expect('{')
            val map = linkedMapOf<String, Value>()
            skipWs()
            if (peek('}')) {
                i++
                return Obj(map)
            }
            while (true) {
                skipWs()
                val key = parseString()
                skipWs()
                expect(':')
                map[key] = parseValue()
                skipWs()
                when {
                    peek('}') -> {
                        i++
                        return Obj(map)
                    }
                    peek(',') -> i++
                    else -> error("expected , or } at $i")
                }
            }
        }

        private fun parseArray(): Arr {
            expect('[')
            val items = mutableListOf<Value>()
            skipWs()
            if (peek(']')) {
                i++
                return Arr(items)
            }
            while (true) {
                items += parseValue()
                skipWs()
                when {
                    peek(']') -> {
                        i++
                        return Arr(items)
                    }
                    peek(',') -> i++
                    else -> error("expected , or ] at $i")
                }
            }
        }

        private fun parseString(): String {
            expect('"')
            val sb = StringBuilder()
            while (!eof()) {
                val ch = src[i++]
                when (ch) {
                    '"' -> return sb.toString()
                    '\\' -> {
                        if (eof()) error("bad escape")
                        when (val e = src[i++]) {
                            '"', '\\', '/' -> sb.append(e)
                            'b' -> sb.append('\b')
                            'f' -> sb.append('\u000c')
                            'n' -> sb.append('\n')
                            'r' -> sb.append('\r')
                            't' -> sb.append('\t')
                            'u' -> {
                                if (i + 4 > src.length) error("bad unicode")
                                val hex = src.substring(i, i + 4)
                                i += 4
                                sb.append(hex.toInt(16).toChar())
                            }
                            else -> error("bad escape \\$e")
                        }
                    }
                    else -> sb.append(ch)
                }
            }
            error("unterminated string")
        }

        private fun parseNumber(): Long {
            val start = i
            if (peek('-')) i++
            while (i < src.length && src[i].isDigit()) i++
            // 忽略小数部分（备份只用整数）
            if (peek('.')) {
                i++
                while (i < src.length && src[i].isDigit()) i++
            }
            if (peek('e') || peek('E')) {
                i++
                if (peek('+') || peek('-')) i++
                while (i < src.length && src[i].isDigit()) i++
            }
            val text = src.substring(start, i)
            return text.toDouble().toLong()
        }

        private fun parseLiteral(lit: String, value: Value): Value {
            if (!src.startsWith(lit, i)) error("expected $lit")
            i += lit.length
            return value
        }

        private fun expect(ch: Char) {
            skipWs()
            if (eof() || src[i] != ch) error("expected $ch at $i")
            i++
        }

        private fun peek(ch: Char): Boolean = i < src.length && src[i] == ch
    }
}
