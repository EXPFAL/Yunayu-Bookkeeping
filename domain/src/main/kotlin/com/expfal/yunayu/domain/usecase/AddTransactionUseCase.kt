package com.expfal.yunayu.domain.usecase

import com.expfal.yunayu.domain.model.Transaction
import com.expfal.yunayu.domain.model.TransactionType
import com.expfal.yunayu.domain.repository.TransactionRepository

/**
 * 新增一笔交易，支撑「3秒极速记账」的快捷录入。
 *
 * 金额单位为「分」（Long）；[type] 指定收支方向，默认支出；[tagId] 为可空学业标签外键；
 * 发生时间默认取当前毫秒。返回新交易的主键。
 */
class AddTransactionUseCase(
    private val transactionRepository: TransactionRepository,
) {

    /** 组装交易并落库，返回主键。 */
    suspend operator fun invoke(
        amountCents: Long,
        tagId: Long?,
        occurredAt: Long = System.currentTimeMillis(),
        type: TransactionType = TransactionType.EXPENSE,
    ): Long = transactionRepository.add(
        Transaction(
            amountCents = amountCents,
            type = type,
            note = null,
            tagId = tagId,
            occurredAt = occurredAt,
        ),
    )
}
