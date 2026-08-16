package com.expfal.yunayu.domain.usecase

import com.expfal.yunayu.domain.model.MergeResult
import com.expfal.yunayu.domain.repository.ReportRepository
import com.expfal.yunayu.domain.repository.TagRepository
import com.expfal.yunayu.domain.repository.TransactionRepository
import kotlinx.coroutines.CancellationException

/**
 * 执行一次标签合并：把 [dropTagId] 下的交易迁移到 [keepTagId]，并标脏受影响报告。
 *
 * 流程：先经 [TransactionRepository.getOccurredAtsByTagIds] 取 [dropTagId] 直接挂载交易的
 * `occurredAt`（合并前快照，供报告标脏）→ [TagRepository.mergeTags] 委托合并（校验失败异常
 * 直接向上抛）→ 对去重后的 `occurredAt` 逐个标脏窗口覆盖报告（标脏失败不阻断，仅
 * [kotlinx.coroutines.CancellationException] 重抛）→ 返回受影响交易数。
 */
class MergeTagsUseCase(
    private val tagRepository: TagRepository,
    private val transactionRepository: TransactionRepository,
    private val reportRepository: ReportRepository,
) {

    /** 合并 [dropTagId] 到 [keepTagId]，返回被迁移的交易数；校验失败异常向上抛。 */
    suspend operator fun invoke(keepTagId: Long, dropTagId: Long): MergeResult {
        val occurredAts = transactionRepository.getOccurredAtsByTagIds(listOf(dropTagId))
        tagRepository.mergeTags(keepTagId, dropTagId)
        for (occurredAt in occurredAts.distinct()) {
            runCatching { reportRepository.invalidateWhereWindowContains(occurredAt) }
                .onFailure { if (it is CancellationException) throw it }
        }
        return MergeResult(affectedTransactionCount = occurredAts.size)
    }
}
