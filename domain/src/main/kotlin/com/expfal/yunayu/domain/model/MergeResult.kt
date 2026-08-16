package com.expfal.yunayu.domain.model

/**
 * 一次标签合并的执行结果。
 *
 * @property affectedTransactionCount 合并前被迁移标签（drop）直接挂载的交易数，
 *   即从 drop 迁移到 keep 的交易条数。
 */
data class MergeResult(
    val affectedTransactionCount: Int,
)
