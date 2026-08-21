package com.expfal.yunayu.domain.model

/**
 * 最近交易摘要行，供首页快捷入口展示（[com.expfal.yunayu.domain.repository.TransactionRepository.observeRecent]）。
 *
 * 仅携带展示所需字段；`tagName` 为可空（交易未挂标签时），`note` 为可空备注（交易未填备注时为 null），
 * `accountName` 为可空账户名（未指定账户时为 null）。
 */
data class RecentTransaction(
    val id: Long,
    val amountCents: Long,
    val type: TransactionType,
    val tagName: String?,
    val occurredAt: Long,
    val note: String? = null,
    val accountName: String? = null,
)
