package com.expfal.yunayu.domain.model

/**
 * 一笔账户间转账。金额一律以「分」为单位（`Long`），避免浮点误差，展示层再格式化为元。
 *
 * [fromAccountId] / [toAccountId] 均指向具体账户（转账不涉及「未指定账户」）；删除任一
 * 账户时，涉及该账户的转账由数据库外键 ON DELETE CASCADE 级联删除。
 */
data class Transfer(
    val id: Long = 0L,
    val fromAccountId: Long,
    val toAccountId: Long,
    val amountCents: Long,
    val note: String? = null,
    val occurredAt: Long = 0L,
)
