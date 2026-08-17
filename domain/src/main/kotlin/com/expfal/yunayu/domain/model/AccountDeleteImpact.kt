package com.expfal.yunayu.domain.model

/**
 * 删除账户的影响面快照（删除确认弹窗数据源）。
 *
 * @property affectedTransactionCount 将被置空账户（`account_id → NULL`）影响的交易数。
 * @property affectedTransferCount 将被级联删除的转账数（该账户作为 from 或 to 的转账）。
 */
data class AccountDeleteImpact(
    val affectedTransactionCount: Int,
    val affectedTransferCount: Int = 0,
)
