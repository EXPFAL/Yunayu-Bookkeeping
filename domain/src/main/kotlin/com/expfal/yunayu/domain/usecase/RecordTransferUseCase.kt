package com.expfal.yunayu.domain.usecase

import com.expfal.yunayu.domain.model.Transfer
import com.expfal.yunayu.domain.repository.TransferRepository

/**
 * 新增一笔账户间转账，支撑快捷录入弹层的「转账」模式。
 *
 * 校验规则：[fromAccountId] / [toAccountId] 必须非 0、二者必须不同、[amountCents] 必须为正；
 * 任一校验失败抛 [IllegalArgumentException]，不产生任何写入。
 *
 * 时间口径对齐 [AddTransactionUseCase]：[occurredAt] 默认取当前毫秒；`createdAt` 由
 * [TransferRepository] 实现侧落库时取当前时间，本类不关心。
 *
 * 转账与收支统计 / 月度预算 / 报告 / 标签推荐完全隔离：不触碰报告标脏、不触碰预算仓储，
 * 这是设计而非遗漏（转账只改变账户余额，不影响任何收支口径）。
 */
class RecordTransferUseCase(
    private val transferRepository: TransferRepository,
) {

    /** 校验并落库一笔转账，返回其主键。 */
    suspend operator fun invoke(
        fromAccountId: Long,
        toAccountId: Long,
        amountCents: Long,
        note: String? = null,
        occurredAt: Long = System.currentTimeMillis(),
    ): Long {
        require(fromAccountId != 0L) { "fromAccountId must be non-zero" }
        require(toAccountId != 0L) { "toAccountId must be non-zero" }
        require(fromAccountId != toAccountId) { "fromAccountId and toAccountId must differ" }
        require(amountCents > 0L) { "amountCents must be positive" }
        return transferRepository.insertTransfer(
            Transfer(
                fromAccountId = fromAccountId,
                toAccountId = toAccountId,
                amountCents = amountCents,
                note = note,
                occurredAt = occurredAt,
            ),
        )
    }
}
