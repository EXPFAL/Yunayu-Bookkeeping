package com.expfal.yunayu.domain.usecase

import com.expfal.yunayu.domain.model.Transaction
import com.expfal.yunayu.domain.model.TransactionType
import com.expfal.yunayu.domain.repository.ReportRepository
import com.expfal.yunayu.domain.repository.TransactionRepository
import kotlinx.coroutines.CancellationException

/**
 * 更新一笔交易的金额 / 类型 / 备注 / 标签 / 账户，并标脏窗口覆盖该交易发生时刻的报告。
 *
 * 校验规则：主键 [Transaction.id] 必须非 0、[Transaction.amountCents] 必须为正、
 * [Transaction.type] 必须是合法枚举值（类型层面已由 [TransactionType] 保证，仍做防御性白名单校验）。
 * 校验失败抛 [IllegalArgumentException]，不产生任何写入。
 *
 * 先更新交易（更新异常向上抛）；更新成功后尽力将窗口覆盖 [Transaction.occurredAt] 的报告状态置
 * FAILED，供报告页手动重试。标脏失败不阻断更新成功：:domain 为纯 JVM 模块、无 Android 日志依赖，
 * 标脏异常被静默吞掉（仅 [CancellationException] 重抛，遵守协程取消语义）。
 */
class UpdateTransactionUseCase(
    private val transactionRepository: TransactionRepository,
    private val reportRepository: ReportRepository,
) {

    /** 校验并更新 [transaction]，随后按 [Transaction.occurredAt] 标脏窗口覆盖该时刻的报告。 */
    suspend operator fun invoke(transaction: Transaction) {
        require(transaction.id != 0L) { "transaction id must be non-zero" }
        require(transaction.amountCents > 0L) { "amountCents must be positive" }
        require(transaction.type in TransactionType.entries) { "invalid transaction type: ${transaction.type}" }
        transactionRepository.updateTransaction(transaction)
        runCatching { reportRepository.invalidateWhereWindowContains(transaction.occurredAt) }
            .onFailure { if (it is CancellationException) throw it }
    }
}
