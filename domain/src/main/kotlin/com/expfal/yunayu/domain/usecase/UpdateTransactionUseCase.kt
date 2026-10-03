package com.expfal.yunayu.domain.usecase

import com.expfal.yunayu.domain.model.Transaction
import com.expfal.yunayu.domain.model.TransactionType
import com.expfal.yunayu.domain.repository.ReportRepository
import com.expfal.yunayu.domain.repository.TransactionRepository
import kotlinx.coroutines.CancellationException

/**
 * 更新一笔交易的金额 / 类型 / 备注 / 标签 / 账户 / 发生时间，并标脏窗口覆盖的报告。
 *
 * 校验规则：主键 [Transaction.id] 必须非 0、[Transaction.amountCents] 必须为正、
 * [Transaction.type] 必须是合法枚举值。校验失败抛 [IllegalArgumentException]，不产生任何写入。
 *
 * 先读取旧记录以取得原 [Transaction.occurredAt]，再更新；成功后对**旧、新**发生时刻各
 * [ReportRepository.invalidateWhereWindowContains] 一次（相同时只标脏一次）。标脏失败不阻断
 * 更新成功（仅 [CancellationException] 重抛）。
 */
class UpdateTransactionUseCase(
    private val transactionRepository: TransactionRepository,
    private val reportRepository: ReportRepository,
) {

    suspend operator fun invoke(transaction: Transaction) {
        require(transaction.id != 0L) { "transaction id must be non-zero" }
        require(transaction.amountCents > 0L) { "amountCents must be positive" }
        require(transaction.type in TransactionType.entries) { "invalid transaction type: ${transaction.type}" }
        val previous = transactionRepository.getById(transaction.id)
        val previousOccurredAt = previous?.occurredAt
        transactionRepository.updateTransaction(transaction)
        val epochs = buildSet {
            previousOccurredAt?.let { add(it) }
            add(transaction.occurredAt)
        }
        for (epoch in epochs) {
            runCatching { reportRepository.invalidateWhereWindowContains(epoch) }
                .onFailure { if (it is CancellationException) throw it }
        }
    }
}
