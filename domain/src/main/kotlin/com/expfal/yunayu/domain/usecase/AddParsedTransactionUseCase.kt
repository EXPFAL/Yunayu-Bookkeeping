package com.expfal.yunayu.domain.usecase

import com.expfal.yunayu.domain.model.Transaction
import com.expfal.yunayu.domain.nl.model.NlTransactionDraft
import com.expfal.yunayu.domain.repository.ReportRepository
import com.expfal.yunayu.domain.repository.TransactionRepository
import kotlinx.coroutines.CancellationException

/**
 * 把自然语言解析草稿直通落库为一笔交易，支撑「自然语言记账」的预览确认保存。
 *
 * 与 [AddTransactionUseCase] 不同：本用例完整保留解析出的 [NlTransactionDraft.type]、
 * [NlTransactionDraft.note] 与 [NlTransactionDraft.occurredAtEpochMillis]，不丢失解析信息。
 * 落库成功后尽力标脏窗口覆盖该发生时刻的报告（仅 [CancellationException] 重抛）。
 * 返回新交易的主键。
 */
class AddParsedTransactionUseCase(
    private val transactionRepository: TransactionRepository,
    private val reportRepository: ReportRepository,
) {

    /** 将草稿映射为 [Transaction] 并落库，返回主键；成功后按发生时刻标脏报告。 */
    suspend operator fun invoke(draft: NlTransactionDraft): Long {
        val id = transactionRepository.add(
            Transaction(
                amountCents = draft.amountCents,
                type = draft.type,
                note = draft.note,
                tagId = draft.tagId,
                accountId = draft.accountId,
                occurredAt = draft.occurredAtEpochMillis,
            ),
        )
        runCatching { reportRepository.invalidateWhereWindowContains(draft.occurredAtEpochMillis) }
            .onFailure { if (it is CancellationException) throw it }
        return id
    }
}
