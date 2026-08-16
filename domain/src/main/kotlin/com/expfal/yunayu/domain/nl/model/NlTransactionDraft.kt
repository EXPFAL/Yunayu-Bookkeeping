package com.expfal.yunayu.domain.nl.model

import com.expfal.yunayu.domain.model.TransactionType

/**
 * 自然语言解析产出的归一化交易草稿。
 *
 * 字段与 [com.expfal.yunayu.domain.model.Transaction] 对齐：金额单位为「分」（`Long`）。
 * [tagPhrase] 为模型给出的标签短语（如「生活·餐饮」），仅用于编排层回填 [tagId]；
 * [tagId] 由 [com.expfal.yunayu.domain.nl.ParseNaturalLanguageTransactionUseCase]
 * 归一匹配候选集后回填，未命中时保持 `null`。
 * [accountId] 为可空账户外键（账户体系迭代），`null` 表示「未指定账户」。
 */
data class NlTransactionDraft(
    val amountCents: Long,
    val type: TransactionType = TransactionType.EXPENSE,
    val tagPhrase: String? = null,
    val note: String? = null,
    val occurredAtEpochMillis: Long,
    val tagId: Long? = null,
    val accountId: Long? = null,
)
