package com.expfal.yunayu.domain.usecase

import com.expfal.yunayu.domain.model.Transfer
import com.expfal.yunayu.domain.repository.TransferRepository

/**
 * 更新一笔账户间转账。
 *
 * 校验与 [RecordTransferUseCase] 对齐：账户非空且不同、金额为正。失败抛
 * [IllegalArgumentException]。不触碰报告标脏（转账与收支口径隔离）。
 */
class UpdateTransferUseCase(
    private val transferRepository: TransferRepository,
) {

    suspend operator fun invoke(transfer: Transfer) {
        require(transfer.id != 0L) { "transfer id must be non-zero" }
        require(transfer.fromAccountId != 0L) { "fromAccountId must be non-zero" }
        require(transfer.toAccountId != 0L) { "toAccountId must be non-zero" }
        require(transfer.fromAccountId != transfer.toAccountId) {
            "fromAccountId and toAccountId must differ"
        }
        require(transfer.amountCents > 0L) { "amountCents must be positive" }
        transferRepository.updateTransfer(transfer)
    }
}
