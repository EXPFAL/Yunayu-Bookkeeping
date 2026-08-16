package com.expfal.yunayu.domain.model

/** 交易方向。 */
enum class TransactionType {
    EXPENSE,
    INCOME,
}

/**
 * 一笔交易。金额一律以「分」为单位（`Long`），避免浮点误差，展示层再格式化为元。
 *
 * `tagId` 为可空的学业标签外键（SCAFFOLD.md 4.3 方案 A：单笔交易最多挂一个学业标签）。
 * `accountId` 为可空的账户外键（账户体系迭代），`null` 表示「未指定账户」。
 */
data class Transaction(
    val id: Long = 0L,
    val amountCents: Long,
    val type: TransactionType = TransactionType.EXPENSE,
    val note: String? = null,
    val tagId: Long? = null,
    val accountId: Long? = null,
    val occurredAt: Long = 0L,
)
