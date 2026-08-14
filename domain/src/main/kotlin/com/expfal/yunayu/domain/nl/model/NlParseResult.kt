package com.expfal.yunayu.domain.nl.model

/** 自然语言解析结果：成功产出草稿，或失败给出原因。 */
sealed interface NlParseResult {

    /** 解析成功，携带归一化交易草稿。 */
    data class Success(val draft: NlTransactionDraft) : NlParseResult

    /** 解析失败，携带具体原因。 */
    data class Failure(val reason: NlParseFailure) : NlParseResult
}
