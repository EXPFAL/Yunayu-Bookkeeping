package com.expfal.yunayu.domain.usecase

import com.expfal.yunayu.domain.repository.ReportRepository
import com.expfal.yunayu.domain.repository.TransactionRepository
import kotlinx.coroutines.CancellationException

/**
 * 删除一笔交易，并标脏窗口覆盖该交易发生时刻的报告。
 *
 * 先删除交易（删除异常向上抛）；删除成功后尽力将窗口覆盖 [occurredAt] 的报告状态置 FAILED，
 * 供报告页手动重试。标脏失败不阻断删除成功：:domain 为纯 JVM 模块、无 Android 日志依赖，
 * 标脏异常被静默吞掉（仅 [CancellationException] 重抛，遵守协程取消语义）。
 */
class DeleteTransactionUseCase(
    private val transactionRepository: TransactionRepository,
    private val reportRepository: ReportRepository,
) {

    /** 删除 [transactionId] 对应的交易，并按 [occurredAt] 标脏窗口覆盖该时刻的报告。 */
    suspend operator fun invoke(transactionId: Long, occurredAt: Long) {
        transactionRepository.delete(transactionId)
        runCatching { reportRepository.invalidateWhereWindowContains(occurredAt) }
            .onFailure { if (it is CancellationException) throw it }
    }
}
