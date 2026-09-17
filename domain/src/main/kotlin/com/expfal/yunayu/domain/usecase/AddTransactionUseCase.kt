package com.expfal.yunayu.domain.usecase

import com.expfal.yunayu.domain.model.Transaction
import com.expfal.yunayu.domain.model.TransactionType
import com.expfal.yunayu.domain.repository.ReportRepository
import com.expfal.yunayu.domain.repository.TransactionRepository
import kotlinx.coroutines.CancellationException

/**
 * 新增一笔交易，支撑「3秒极速记账」的快捷录入。
 *
 * 金额单位为「分」（Long）；[type] 指定收支方向，默认支出；[tagId] 为可空学业标签外键；
 * [accountId] 为可空账户外键（默认 null 表示「未指定账户」）；发生时间默认取当前毫秒。
 * 落库成功后尽力将窗口覆盖 [occurredAt] 的报告状态置 STALE；标脏失败不阻断新增成功
 * （仅 [CancellationException] 重抛）。返回新交易的主键。
 */
class AddTransactionUseCase(
    private val transactionRepository: TransactionRepository,
    private val reportRepository: ReportRepository,
) {

    /** 组装交易并落库，返回主键；成功后按发生时刻标脏覆盖窗口的报告。 */
    suspend operator fun invoke(
        amountCents: Long,
        tagId: Long?,
        occurredAt: Long = System.currentTimeMillis(),
        type: TransactionType = TransactionType.EXPENSE,
        accountId: Long? = null,
        note: String? = null,
    ): Long {
        val id = transactionRepository.add(
            Transaction(
                amountCents = amountCents,
                type = type,
                note = note,
                tagId = tagId,
                accountId = accountId,
                occurredAt = occurredAt,
            ),
        )
        runCatching { reportRepository.invalidateWhereWindowContains(occurredAt) }
            .onFailure { if (it is CancellationException) throw it }
        return id
    }
}
