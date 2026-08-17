package com.expfal.yunayu.domain.model

/**
 * 账户余额快照，供「持有资金」按账户分组展示。
 *
 * @property accountId 账户 id；`null` 表示「未指定账户」分组（历史交易或未选账户的交易）。
 * @property accountName 账户名；`null` 仅出现在未指定账户分组（无对应账户名）。
 * @property balanceCents 该账户余额（分）= 期初余额 + 交易净额（收入−支出）+ 转账净额（转入−转出）；
 *   未指定账户无期初与转账，仅含交易净额。
 */
data class AccountBalance(
    val accountId: Long?,
    val accountName: String?,
    val balanceCents: Long,
)
