package com.expfal.yunayu.domain.usecase

import com.expfal.yunayu.domain.repository.TransferRepository

/** 删除一笔转账（按主键），与 [DeleteTransactionUseCase] 对称的薄用例。 */
class DeleteTransferUseCase(
    private val transferRepository: TransferRepository,
) {

    /** 删除 [transferId] 对应的转账记录。 */
    suspend operator fun invoke(transferId: Long) {
        transferRepository.deleteById(transferId)
    }
}
