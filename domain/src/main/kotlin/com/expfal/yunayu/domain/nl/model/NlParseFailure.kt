package com.expfal.yunayu.domain.nl.model

/** 自然语言解析失败原因。 */
enum class NlParseFailure {
    /** 引擎不可用或推理出错。 */
    ENGINE_UNAVAILABLE,

    /** 输出中缺少或无法解析出有效金额。 */
    NO_AMOUNT,

    /** 输出无法抽取为合法 JSON（或结构畸形）。 */
    MALFORMED_OUTPUT,

    /** 输入文本为空或空白。 */
    EMPTY_INPUT,
}
