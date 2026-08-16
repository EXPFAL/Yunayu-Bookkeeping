package com.expfal.yunayu.domain.model

/**
 * 交易账户筛选三态，供 [com.expfal.yunayu.domain.repository.TransactionRepository.observeFiltered]
 * 按账户维度过滤交易。
 *
 * - [All]：不按账户过滤，返回全部交易（含未指定账户与各指定账户）。
 * - [Unspecified]：仅返回未指定账户（`account_id IS NULL`）的交易。
 * - [Specific]：仅返回归属指定账户 [Specific.accountId] 的交易。
 */
sealed interface AccountFilter {

    /** 全部账户（不设账户过滤条件）。 */
    data object All : AccountFilter

    /** 仅未指定账户（`account_id IS NULL`）。 */
    data object Unspecified : AccountFilter

    /** 仅指定账户。 */
    data class Specific(val accountId: Long) : AccountFilter
}
