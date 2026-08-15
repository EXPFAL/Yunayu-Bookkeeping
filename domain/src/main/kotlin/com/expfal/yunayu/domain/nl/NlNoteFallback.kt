package com.expfal.yunayu.domain.nl

/**
 * 自然语言记账备注的本地启发式兜底。
 *
 * 仅在模型未输出 `note` 时由 [NlOutputParser] 调用：从原始输入句中剥离金额、日期、标签
 * 短语与常见填充词后，取剩余主干核心词作为备注。属轻量启发式，不做中文分词与语义理解，
 * 模型输出始终优先，本对象只负责兜底保证每条记录备注非空。
 */
object NlNoteFallback {

    /**
     * 从 [originalInput] 提取兜底备注。
     *
     * 处理顺序：日期 → 金额 → 标签短语 → 填充词与标点空白 → 截断至 [MAX_NOTE_LENGTH] 字。
     * 日期先于金额剥离，避免金额正则把「YYYY-MM-DD」拆散。截断经 [truncateNote] 避免 UTF-16
     * 孤立代理。剥离后不足 [MIN_NOTE_LENGTH] 字或输入为空时返回 `null`，保持 `note` 可空语义。
     */
    fun extractNote(originalInput: String?, tagPhrase: String?): String? {
        val raw = originalInput?.trim().orEmpty()
        if (raw.isEmpty()) return null

        var text = raw
        text = DATE_PATTERN.replace(text, " ")
        text = AMOUNT_PATTERN.replace(text, " ")
        text = stripTagPhrase(text, tagPhrase)
        text = FILLER_PATTERN.replace(text, " ")
        text = text.filterNot { it.isWhitespace() || it in PUNCTUATION }

        return truncateNote(text).takeIf { it.length >= MIN_NOTE_LENGTH }
    }

    /**
     * 将 [text] 截断至 [MAX_NOTE_LENGTH] 字；若截断处为 UTF-16 高位代理（high surrogate），
     * 回退一位避免产生孤立代理。空串原样返回。
     */
    internal fun truncateNote(text: String): String {
        val truncated = text.take(MAX_NOTE_LENGTH)
        val last = truncated.lastOrNull() ?: return truncated
        return if (last.isHighSurrogate()) truncated.dropLast(1) else truncated
    }

    /** 剥离标签短语及其「·」分隔的子变体（如「生活·餐饮」同时剥「生活」「餐饮」）。 */
    private fun stripTagPhrase(text: String, tagPhrase: String?): String {
        if (tagPhrase.isNullOrBlank()) return text
        var result = text
        val variants = (listOf(tagPhrase.trim()) + tagPhrase.split(TAG_SEPARATOR).map { it.trim() })
            .filter { it.isNotEmpty() }
            .distinct()
            .sortedByDescending { it.length }
        variants.forEach { variant -> result = result.replace(variant, "") }
        return result
    }

    /** 相对日期词、「YYYY-MM-DD」绝对日期与「M月D日/号」非标日期。 */
    private val DATE_PATTERN = Regex(
        """(?:大前天|前天|昨天|今天|\d{4}[-/]\d{1,2}[-/]\d{1,2}|\d{1,2}月\d{1,2}[日号])""",
    )

    /** 金额：可选货币前缀 + 数字/小数 + 可选货币/量词后缀（元块毛分角万千 kK ¥￥）。 */
    private val AMOUNT_PATTERN = Regex("""[¥￥]?\s*\d+(?:\.\d+)?\s*[元块毛分角万千kK]?""")

    /** 常见记账填充词，多字动词组与单字虚词，多字词优先匹配。 */
    private val FILLER_PATTERN = Regex("""花了|用了|买了|充了|交了|付了|收到|了|吧|的""")

    /** 备注中剥离的标点符号。 */
    private const val PUNCTUATION = "，。！？、；：,.!?;:()[]{}《》【】·…～-—/"

    /** 备注长度上界（字符）。 */
    private const val MAX_NOTE_LENGTH = 8

    /** 备注长度下界（字符），不足视为无有效备注。 */
    private const val MIN_NOTE_LENGTH = 2

    /** 标签短语层级分隔符。 */
    private const val TAG_SEPARATOR = '·'
}
