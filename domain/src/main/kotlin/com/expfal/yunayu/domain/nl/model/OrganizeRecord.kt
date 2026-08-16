package com.expfal.yunayu.domain.nl.model

import com.expfal.yunayu.domain.model.TransactionType

/**
 * 整理输入记录：待整理未分类交易的最小字段集。
 *
 * 字段对齐 [com.expfal.yunayu.domain.model.RecentTransaction]，仅携带提示词构建与
 * 建议应用所需信息；[note] 为可空备注，[type] 用于收入体系白名单判断。
 */
data class OrganizeRecord(
    val id: Long,
    val note: String?,
    val amountCents: Long,
    val type: TransactionType,
    val occurredAt: Long,
)
